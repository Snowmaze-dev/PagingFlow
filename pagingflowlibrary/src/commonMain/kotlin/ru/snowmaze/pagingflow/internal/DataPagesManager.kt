package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.diff.AwaitDataSetEvent
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.EventFromDataSource
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.DefaultPagingDataChangesMedium
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.source.PageLoaderConfig
import ru.snowmaze.pagingflow.source.MaxItemsConfiguration
import ru.snowmaze.pagingflow.utils.fastForEach
import ru.snowmaze.pagingflow.utils.fastIndexOfFirst
import ru.snowmaze.pagingflow.utils.fastIndexOfLast
import ru.snowmaze.pagingflow.utils.fastSumOf
import ru.snowmaze.pagingflow.utils.platformMapOf
import kotlin.concurrent.Volatile

internal class DataPagesManager<Key : Any, Data : Any>(
    private val pageLoaderConfig: PageLoaderConfig<Key>,
    private val setDataMutex: Mutex,
    private val pagingSourcesManager: PagingSourcesManager<Key, Data>
) : DefaultPagingDataChangesMedium<Key, Data>() {

    private val _dataPages = mutableListOf<DataPage<Key, Data>>()
    val dataPages get() = _dataPages
    val isNotNullified = { item: DataPage<Key, Data> -> !item.isNullified }

    private var cachedData = platformMapOf<Int, Pair<Key?, PagingParams>>()
    val currentPagesCount get() = dataPages.size

    private var lastPaginationDirection = true

    @Volatile
    private var isAnyDataChanged = false

    override val config = DataChangesMediumConfig(
        pageLoaderConfig.coroutineScope,
        pageLoaderConfig.processingDispatcher
    )

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        if (!pageLoaderConfig.shouldStorePageItems && isAnyDataChanged) {
            throw IllegalStateException("Can't subscribe after data is loaded because shouldStorePageItems = false.")
        }
        super.addDataChangedCallback(callback)
        if (isAnyDataChanged) config.coroutineScope.launch {
            setDataMutex.withLock {
                resendAllPages(listOf(callback))
            }
        }
    }

    suspend fun invalidate(
        invalidateBehavior: InvalidateBehavior? = null,
        skipPage: DataPage<Key, Data>? = null,
        removeCachedData: Boolean = true,
    ) {
        for (page in dataPages) {
            if (page == skipPage) continue
            page.listenJob.cancel()
            page.data = null
        }
        _dataPages.clear()
        skipPage?.let { _dataPages.add(it) }
        if (removeCachedData) cachedData.clear()
        isAnyDataChanged = false
        notifyOnEvent(InvalidateEvent(invalidateBehavior))
    }

    fun removePagingSourcePages(dataSourceIndex: Int) {
        _dataPages.removeAll { it.dataSourceIndex == dataSourceIndex }
        updateIndexes()
    }

    fun movePages(fromDataSourceIndex: Int, toDataSourceIndex: Int) {
        val fromPages = _dataPages.filter { it.dataSourceIndex == fromDataSourceIndex }
        if (fromPages.isEmpty()) return

        val firstFromIndex =
            _dataPages.fastIndexOfFirst { it.dataSourceIndex == fromDataSourceIndex }
        val firstToIndex = _dataPages.fastIndexOfLast { it.dataSourceIndex == toDataSourceIndex }
            .coerceAtLeast(0)
        repeat(fromPages.size) { _dataPages.removeAt(firstFromIndex) }
        _dataPages.addAll(firstToIndex, fromPages)
        updateIndexes()
    }

    fun updateIndexes() {
        val newCachedData = platformMapOf<Int, Pair<Key?, PagingParams>>()
        for (index in _dataPages.indices) {
            _dataPages[index].apply {
                val pagingSource = pagingSourceWithIndex.first
                cachedData[pageIndex]?.let { newCachedData[index] = it }
                pageIndex = index
                dataSourceIndex = pagingSourcesManager.getSourceIndex(pagingSource)
                pagingSourceWithIndex = pagingSource to dataSourceIndex

                val previousPage = _dataPages.getOrNull(index - 1)
                val isPreviousPageHaveSameDataSource =
                    previousPage?.pagingSourceWithIndex?.first == pagingSource
                previousPageKey = if (isPreviousPageHaveSameDataSource) {
                    previousPage?.currentPageKey
                } else null
                val nextPage = _dataPages.getOrNull(index + 1)
                nextPageKey = if (isPreviousPageHaveSameDataSource) {
                    nextPage?.currentPageKey
                } else null
                pageIndexInPagingSource = if (isPreviousPageHaveSameDataSource) {
                    previousPage?.pageIndexInPagingSource?.let { it + 1 } ?: 0
                } else 0
            }
        }
        cachedData = newCachedData
    }

    suspend fun resendAllPages(
        toCallbacks: List<DataChangedCallback<Key, Data>> = dataChangedCallbacks
    ) {
        val events = buildList(1 + dataPages.size) {
            for (page in dataPages) {
                add(
                    PageAddedEvent(
                        key = page.currentPageKey,
                        sourceIndex = page.dataSourceIndex,
                        pageIndexInSource = page.pageIndexInPagingSource,
                        pageIndex = page.pageIndex,
                        items = page.data?.data ?: continue,
                        params = page.data?.params
                    )
                )
            }
            add(
                0, if (isEmpty()) InvalidateEvent(InvalidateBehavior.INVALIDATE_IMMEDIATELY)
                else InvalidateEvent()
            )
        }
        toCallbacks.fastForEach { it.onEvents(events) }
    }

    fun getCachedData(index: Int) = cachedData[index]

    /**
     * Adding page to pages list and starting listening to page changes
     */
    suspend inline fun setupPage(
        newIndex: Int,
        result: LoadResult.Success<Key, Data>,
        page: DataPage<Key, Data>,
        loadParams: LoadParams<Key>,
        shouldReplaceOnConflict: Boolean,
        awaitDataSetChannel: Channel<Unit>?,
        crossinline onPageRemoved: (inBeginning: Boolean, pageIndex: Int) -> Unit,
        crossinline onLastPageNextKeyChanged: suspend (Key?, Boolean) -> Unit,
    ) {
        isAnyDataChanged = true
        result.cachedResult?.let { cachedData[page.pageIndex] = page.currentPageKey to it }
        val isExistingPage = dataPages.getOrNull(newIndex) != null
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        if (isExistingPage && shouldReplaceOnConflict) _dataPages[newIndex] = page
        else _dataPages.add(newIndex.coerceAtLeast(0), page)
        val invalidateData =
            loadParams.pagingParams?.getOrNull(PagingLibraryParamsKeys.InvalidateData) ?: false

        // page flow listening starts here
        lastPaginationDirection = isPaginationDown

        page.itemCount = 0

        if (result is LoadResult.Success.FlowSuccess && result.dataFlow != null) {
            var isFirst = true
            val setData: suspend (data: UpdatableData<Key, Data>?) -> Unit = { value ->
                setDataMutex.withLock {
                    try {
                        setNewPageData(
                            page = page,
                            value = value,
                            invalidateData = invalidateData,
                            isExistingPage = isExistingPage,
                            awaitDataSetChannel = awaitDataSetChannel,
                            onPageRemoved = onPageRemoved,
                            onNextKeyChanged = onLastPageNextKeyChanged,
                            isFirst = isFirst,
                            isPaginationDown = isPaginationDown
                        )
                    } catch (e: Throwable) {
                        if (isFirst) awaitDataSetChannel?.send(Unit)
                    }
                    isFirst = false
                }
            }

            pageLoaderConfig.coroutineScope.launch(
                pageLoaderConfig.processingDispatcher + page.listenJob
            ) {
                if (pageLoaderConfig.shouldCollectOnlyLatest) result.dataFlow
                    .buffer(0, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                    .collect(setData)
                else result.dataFlow.collect(setData)
            }
        } else if (result is LoadResult.Success.SimpleSuccess && result.data != null) try {
            setNewPageData(
                page = page,
                value = UpdatableData(
                    data = result.data,
                    nextPageKey = result.nextPageKey,
                    params = result.returnData
                ),
                invalidateData = invalidateData,
                isExistingPage = isExistingPage,
                awaitDataSetChannel = awaitDataSetChannel,
                onPageRemoved = onPageRemoved,
                onNextKeyChanged = onLastPageNextKeyChanged,
                isFirst = true,
                isPaginationDown = isPaginationDown
            )
        } catch (e: Throwable) {
            awaitDataSetChannel?.send(Unit)
        } else {
            awaitDataSetChannel?.send(Unit)
        }
    }

    private suspend inline fun setNewPageData(
        page: DataPage<Key, Data>,
        value: UpdatableData<Key, Data>?,
        invalidateData: Boolean,
        isExistingPage: Boolean,
        awaitDataSetChannel: Channel<Unit>?,
        crossinline onPageRemoved: (inBeginning: Boolean, pageIndex: Int) -> Unit,
        crossinline onNextKeyChanged: suspend (Key?, Boolean) -> Unit,
        isFirst: Boolean,
        isPaginationDown: Boolean
    ) {
        if (value == null) return
        val isPageDataSizeChanged =
            value.data.size != (page.data?.data?.size ?: page.itemCount ?: 0)
        if (pageLoaderConfig.shouldStorePageItems) page.data = value
        page.itemCount = value.data.size
        val trimEvents = if (isPageDataSizeChanged) trimPages(lastPaginationDirection, page)
        else emptyList()
        val currentKey = page.currentPageKey
        val event = if (isFirst) {
            if (invalidateData) invalidate(skipPage = page)
            if (isExistingPage) PageChangedEvent(
                key = currentKey,
                sourceIndex = page.dataSourceIndex,
                pageIndexInSource = page.pageIndexInPagingSource,
                pageIndex = page.pageIndex,
                items = value.data,
                changeType = PageChangedEvent.ChangeType.CHANGE_FROM_NULLS_TO_ITEMS,
                params = value.params
            ) else PageAddedEvent(
                key = currentKey,
                sourceIndex = page.dataSourceIndex,
                pageIndex = page.pageIndex,
                pageIndexInSource = page.pageIndexInPagingSource,
                items = value.data,
                params = value.params
            )
        } else PageChangedEvent(
            key = currentKey,
            sourceIndex = page.dataSourceIndex,
            pageIndex = page.pageIndex,
            pageIndexInSource = page.pageIndexInPagingSource,
            items = value.data,
            params = value.params,
        )
        val shouldAwaitFirst = awaitDataSetChannel != null && isFirst
        val awaitChannel = if (pageLoaderConfig.shouldCollectOnlyLatest) Channel<Unit>(1)
        else null
        val awaitEvent = if (shouldAwaitFirst ||
            pageLoaderConfig.shouldCollectOnlyLatest
        ) AwaitDataSetEvent<Key, Data> {
            awaitDataSetChannel?.trySend(Unit)
            awaitChannel?.trySend(Unit)
        } else null
        val notified = if (trimEvents.isNullOrEmpty()) {
            if (awaitEvent != null) notifyOnEvents(listOf(awaitEvent, event))
            else notifyOnEvent(event)
        } else {
            var shouldSendEvent = true

            for (trimEvent in trimEvents) {
                onPageRemoved(isPaginationDown, trimEvent.pageIndex)
                if (trimEvent.pageIndex == page.pageIndex) shouldSendEvent = false
            }

            val events = ArrayList<DataChangedEvent<Key, Data>>(
                trimEvents.size + if (shouldSendEvent) 1 else 0
                        + if (awaitEvent == null) 0 else 1
            )
            if (awaitEvent != null) events.add(awaitEvent)
            if (shouldSendEvent) events.add(event)
            events.addAll(trimEvents as List<DataChangedEvent<Key, Data>>)
            notifyOnEvents(events)
        }
        val isLastPageChanged: Boolean
        if (isPaginationDown) {
            isLastPageChanged = page == dataPages.lastOrNull()
            page.nextPageKey = value.nextPageKey
        } else {
            isLastPageChanged = page == dataPages.firstOrNull { !it.isNullified }
            page.previousPageKey = value.nextPageKey
        }
        if (isLastPageChanged) onNextKeyChanged(value.nextPageKey, isPaginationDown)
        if (!notified) {
            delay(1000)
            awaitEvent?.callback?.invoke()
        } else if (pageLoaderConfig.shouldCollectOnlyLatest) awaitChannel?.receive()

        if (page.isCancelled) page.listenJob.cancel()
    }

    /**
     * Removes pages that not needed anymore
     * Also can replace removed pages to null pages if [MaxItemsConfiguration.enableDroppedPagesNullPlaceholders] is true
     */
    private fun trimPages(
        isPaginationDown: Boolean,
        currentPage: DataPage<Key, Data>
    ): List<EventFromDataSource<Key, Data>>? {
        val trimConfig = pageLoaderConfig.maxItemsConfiguration ?: return null
        val maxItemsCount = trimConfig.maxItemsCount.takeIf { it != 0 } ?: return null
        val itemsCount = dataPages.fastSumOf { it.data?.data?.size ?: it.itemCount ?: 0 }
        if (itemsCount > maxItemsCount) {

            // заменить на удаляемую страницу
            val pageIndex = if (isPaginationDown) dataPages.fastIndexOfFirst(isNotNullified)
            else dataPages.fastIndexOfLast(isNotNullified)
            val page = dataPages.getOrNull(pageIndex) ?: return null

            // TODO поменять расчёт индекса для удаления кэша
            val maxCachedResultPagesCount = trimConfig.maxCachedResultPagesCount
            if (maxCachedResultPagesCount != null) {
                val removeCacheIndex = page.pageIndex + if (
                    isPaginationDown
                ) -maxCachedResultPagesCount
                else maxCachedResultPagesCount
                cachedData.remove(removeCacheIndex)
            }

            val trimEvent = if (trimConfig.enableDroppedPagesNullPlaceholders &&
                (isPaginationDown && page.pageIndex >= 0) ||
                (!isPaginationDown && 0 > page.pageIndex)
            ) {
                if (page == currentPage) page.isCancelled = true
                else page.listenJob.cancel()

                val lastData = page.data?.data
                val itemCount = lastData?.size ?: page.itemCount ?: return null
                val trimEvent = PageChangedEvent(
                    key = page.currentPageKey,
                    sourceIndex = page.dataSourceIndex,
                    pageIndex = page.pageIndex,
                    pageIndexInSource = page.pageIndexInPagingSource,
                    items = buildList<Data?>(itemCount) {
                        repeat(itemCount) {
                            add(null)
                        }
                    },
                    changeType = PageChangedEvent.ChangeType.CHANGE_TO_NULLS
                )
                page.data = null
                page.itemCount = null
                trimEvent
            } else {
                if (page == currentPage) page.isCancelled = true
                else page.listenJob.cancel()
                _dataPages.removeAt(pageIndex)

                val trimEvent = PageRemovedEvent<Key, Data>(
                    key = page.currentPageKey,
                    sourceIndex = page.dataSourceIndex,
                    pageIndex = page.pageIndex,
                    pageIndexInSource = page.pageIndexInPagingSource,
                    itemsCount = page.data?.data?.size ?: page.itemCount ?: 0
                )
                page.data = null
                page.itemCount = null
                trimEvent
            }
            val anotherEvents = trimPages(isPaginationDown, currentPage)

            return if (anotherEvents == null) listOf(trimEvent)
            else buildList(1 + anotherEvents.size) {
                add(trimEvent)
                addAll(anotherEvents)
            }
        }
        return null
    }
}

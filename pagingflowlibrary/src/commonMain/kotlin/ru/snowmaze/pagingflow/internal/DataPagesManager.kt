package ru.snowmaze.pagingflow.internal

import androidx.collection.MutableScatterMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.diff.AwaitDataSetEvent
import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.EventFromDataSource
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.OnDataLoaded
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.DefaultPagingEventsMedium
import ru.snowmaze.pagingflow.params.MutablePagingParams
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.source.ConcatPagingSourceConfig
import ru.snowmaze.pagingflow.utils.fastForEach
import ru.snowmaze.pagingflow.utils.fastIndexOfFirst
import ru.snowmaze.pagingflow.utils.fastIndexOfLast
import ru.snowmaze.pagingflow.utils.fastSumOf
import kotlin.concurrent.Volatile
import ru.snowmaze.pagingflow.utils.fastFirstOrNull

internal class DataPagesManager<Key : Any, Data : Any>(
    private val pageLoaderConfig: ConcatPagingSourceConfig<Key>,
    private val setDataMutex: Mutex,
    private val pagingSourcesManager: PagingSourcesManager<Key, Data>
) : DefaultPagingEventsMedium<Key, Data>() {

    private val _dataPages = mutableListOf<DataPage<Key, Data>>()
    val dataPages get() = _dataPages
    val isNotNullified = { item: DataPage<Key, Data> -> !item.isNullified }

    private var cachedData = MutableScatterMap<Int, Pair<Key?, MutablePagingParams>>()
    val pagesCount get() = dataPages.size

    private var lastPaginationDirection = true

    @Volatile
    private var isAnyDataChanged = false

    override val config = PagingEventsMediumConfig(
        pageLoaderConfig.coroutineScope, pageLoaderConfig.processingContext
    )

    override fun addPagingEventsListener(listener: PagingEventsListener<Key, Data>) {
        if (!pageLoaderConfig.storePageItems && isAnyDataChanged) {
            throw IllegalStateException("Can't subscribe after data is loaded because shouldStorePageItems = false.")
        }
        super.addPagingEventsListener(listener)
        if (isAnyDataChanged) config.coroutineScope.launch {
            setDataMutex.withLock {
                resendAllPages(listOf(listener))
            }
        }
    }

    override fun removePagingEventsListener(listener: PagingEventsListener<Key, Data>): Boolean {
        return super.removePagingEventsListener(listener) // TODO cancel flow collectors when no listeners
    }

    fun notifyIfNeeded(pagingParams: PagingParams?) {
        if (!isAnyDataChanged) config.coroutineScope.launch(config.processingContext) {
            setDataMutex.withLock {
                notifyOnEvent(OnDataLoaded(pagingParams))
            }
        }
    }

    suspend fun invalidate(
        invalidateBehavior: InvalidateBehavior? = null,
        removeCachedData: Boolean = true,
        skipPage: DataPage<Key, Data>? = null,
    ) {
        val dataPages = _dataPages
        for (page in dataPages) {
            if (page == skipPage) continue
            page.listenJob.cancel()
            page.data = null
        }
        isAnyDataChanged = false
        if (dataPages.isNotEmpty()) {
            val invalidateChannel = Channel<Unit>(1)
            notifyOnEvents(
                listOf(
                    AwaitDataSetEvent { invalidateChannel.send(Unit) },
                    InvalidateEvent(invalidateBehavior)
                )
            )
            invalidateChannel.receive()
        } else {
            notifyOnEvent(InvalidateEvent(invalidateBehavior))
        }

        dataPages.clear()
        skipPage?.let { dataPages.add(it) }
        if (removeCachedData) cachedData.clear()
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
        val firstToIndex =
            _dataPages.fastIndexOfLast { it.dataSourceIndex == toDataSourceIndex }.coerceAtLeast(0)
        repeat(fromPages.size) { _dataPages.removeAt(firstFromIndex) }
        _dataPages.addAll(firstToIndex, fromPages)
        updateIndexes()
    }

    fun updateIndexes() {
        val newCachedData = MutableScatterMap<Int, Pair<Key?, MutablePagingParams>>()
        val dataPages = _dataPages
        for (index in dataPages.indices) {
            dataPages[index].apply {
                val pagingSource = pagingSourceWithIndex.first
                cachedData[pageIndex]?.let { newCachedData[index] = it }
                pageIndex = index
                dataSourceIndex = pagingSourcesManager.getSourceIndex(pagingSource)
                pagingSourceWithIndex = pagingSource to dataSourceIndex

                val previousPage = dataPages.getOrNull(index - 1)
                val isPreviousPageHaveSameDataSource =
                    previousPage?.pagingSourceWithIndex?.first == pagingSource
                previousPageKey = if (isPreviousPageHaveSameDataSource) {
                    previousPage.currentPageKey
                } else null
                val nextPage = dataPages.getOrNull(index + 1)
                nextPageKey = if (isPreviousPageHaveSameDataSource) {
                    nextPage?.currentPageKey
                } else null
                pageIndexInPagingSource = if (isPreviousPageHaveSameDataSource) {
                    previousPage.pageIndexInPagingSource + 1
                } else 0
            }
        }
        cachedData = newCachedData
    }

    suspend fun resendAllPages(
        toCallbacks: Collection<PagingEventsListener<Key, Data>> = pagingEventsListeners
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
                else InvalidateEvent(null)
            )
        }
        toCallbacks.forEach { it.onEvents(events) }
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
        val invalidateData = loadParams.pagingParams?.getOrNull(
            PagingLibraryParamsKeys.InvalidateData
        ) == true

        // page flow listening starts here
        lastPaginationDirection = isPaginationDown

        page.itemCount = 0

        if (result is LoadResult.Success.FlowSuccess && result.dataFlow != null) {

            pageLoaderConfig.coroutineScope.launch(
                pageLoaderConfig.processingContext + page.listenJob
            ) {
                var isFirst = true
                val setData: suspend (data: UpdatableData<Key, Data>?) -> Unit = { value ->
                    setDataMutex.lock()
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
                    } finally {
                        setDataMutex.unlock()
                        if (isFirst) awaitDataSetChannel?.send(Unit)
                        isFirst = false
                    }
                }
                if (pageLoaderConfig.collectOnlyLatest) result.dataFlow.buffer(
                    RENDEZVOUS,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                ).collect(setData)
                else result.dataFlow.collect(setData)
            }
        } else if (result is LoadResult.Success.SimpleSuccess && result.data != null) try {
            setNewPageData(
                page = page,
                value = UpdatableData(
                    data = result.data, nextPageKey = result.nextPageKey, params = result.returnData
                ),
                invalidateData = invalidateData,
                isExistingPage = isExistingPage,
                awaitDataSetChannel = awaitDataSetChannel,
                onPageRemoved = onPageRemoved,
                onNextKeyChanged = onLastPageNextKeyChanged,
                isFirst = true,
                isPaginationDown = isPaginationDown
            )
        } finally {
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
        val previousItemCount = page.itemCount ?: 0

        val isPageDataSizeChanged = value.data.size != page.currentItemCount
        var isDataChanged = true
        if (pageLoaderConfig.storePageItems) {
            isDataChanged = page.data?.data !== value.data
            page.data = value
        }
        page.itemCount = value.data.size

        val isLastPageChanged: Boolean
        if (isPaginationDown) {
            isLastPageChanged = page === dataPages.lastOrNull()
            page.nextPageKey = value.nextPageKey
        } else {
            isLastPageChanged = page === dataPages.fastFirstOrNull { !it.isNullified }
            page.previousPageKey = value.nextPageKey
        }
        if (isLastPageChanged) onNextKeyChanged(value.nextPageKey, isPaginationDown)

        if (isDataChanged) {
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
                    params = value.params,
                    previousItemCount = previousItemCount
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
                previousItemCount = previousItemCount
            )
            val shouldAwaitFirst = awaitDataSetChannel != null && isFirst
            val awaitChannel = if (pageLoaderConfig.collectOnlyLatest) Channel<Unit>(1)
            else null
            val awaitEvent = if (shouldAwaitFirst || pageLoaderConfig.collectOnlyLatest) {
                AwaitDataSetEvent<Key, Data> {
                    awaitDataSetChannel?.trySend(Unit)
                    awaitChannel?.trySend(Unit)
                }
            } else null
            val notified = if (trimEvents.isNullOrEmpty()) {
                if (awaitEvent != null) {
                    notifyOnEvents(ArrayList<PagingEvent<Key, Data>>(2).also {
                        it.add(awaitEvent)
                        it.add(event)
                    })
                } else notifyOnEvent(event)
            } else {
                var shouldSendEvent = true

                trimEvents.fastForEach { trimEvent ->
                    onPageRemoved(isPaginationDown, trimEvent.pageIndex)
                    if (trimEvent.pageIndex == page.pageIndex) shouldSendEvent = false
                }

                val events = ArrayList<PagingEvent<Key, Data>>(
                    trimEvents.size + if (shouldSendEvent) 1 else 0 + if (awaitEvent == null) 0 else 1
                )
                if (awaitEvent != null) events.add(awaitEvent)
                if (shouldSendEvent) events.add(event)
                events.addAll(trimEvents as List<PagingEvent<Key, Data>>)
                notifyOnEvents(events)
            }
            if (!notified) {
                if (awaitEvent != null) {
                    delay(1000)
                    awaitEvent.callback.invoke()
                }
            } else if (pageLoaderConfig.collectOnlyLatest) awaitChannel?.receive()
        }

        if (page.isCancelled) page.listenJob.cancel()
    }

    /**
     * Removes pages that not needed anymore
     * Also can replace removed pages to null pages if [MaxItemsConfiguration.enableDroppedPagesNullPlaceholders] is true
     */
    private fun trimPages(
        isPaginationDown: Boolean, currentPage: DataPage<Key, Data>
    ): List<EventFromDataSource<Key, Data>>? {
        val trimConfig = pageLoaderConfig.maxItemsConfiguration ?: return null
        val maxItemsCount = trimConfig.maxItemsCount.takeIf { it != 0 } ?: return null
        val itemsCount = dataPages.fastSumOf { it.data?.data?.size ?: it.itemCount ?: 0 }
        if (itemsCount > maxItemsCount) {

            val pageIndex = if (isPaginationDown) dataPages.fastIndexOfFirst(isNotNullified)
            else dataPages.fastIndexOfLast(isNotNullified)
            val page = dataPages.getOrNull(pageIndex) ?: return null

            val maxCachedResultPagesCount = trimConfig.maxCachedResultPagesCount
            if (maxCachedResultPagesCount != null && cachedData.size > maxCachedResultPagesCount) {
                var maxKey = Int.MIN_VALUE
                var minKey = Int.MAX_VALUE
                cachedData.forEachKey {
                    if (it > maxKey) maxKey = it
                    if (minKey > it) minKey = it
                }

                val removeCacheIndex = if (isPaginationDown) {
                    maxKey - maxCachedResultPagesCount
                } else {
                    minKey + maxCachedResultPagesCount
                }
                if (isPaginationDown) {
                    for (i in minKey..removeCacheIndex) {
                        cachedData.remove(i)
                    }
                } else {
                    for (i in removeCacheIndex..maxKey) {
                        cachedData.remove(i)
                    }
                }
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
                    changeType = PageChangedEvent.ChangeType.CHANGE_TO_NULLS,
                    previousItemCount = page.currentItemCount
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
                    itemsCount = page.currentItemCount
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

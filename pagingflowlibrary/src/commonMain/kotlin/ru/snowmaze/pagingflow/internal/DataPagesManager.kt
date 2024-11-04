package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.diff.AwaitDataSetEvent
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.sources.PageLoaderConfig
import ru.snowmaze.pagingflow.sources.MaxItemsConfiguration
import kotlin.concurrent.Volatile

internal class DataPagesManager<Key : Any, Data : Any>(
    private val pageLoaderConfig: PageLoaderConfig<Key>,
    private val setDataMutex: Mutex,
    private val dataSourcesManager: DataSourcesManager<Key, Data>
) : PagingDataChangesMedium<Key, Data> {

    private val _dataPages = mutableListOf<DataPage<Key, Data>>()
    val dataPages get() = _dataPages

    private var cachedData = mutableMapOf<Int, Pair<Key?, PagingParams>>()
    val currentPagesCount get() = dataPages.size

    private var lastPaginationDirection = true

    @Volatile
    private var isAnyDataChanged = false

    private val dataChangedCallbacks = mutableListOf<DataChangedCallback<Key, Data>>()

    override val config = DataChangesMediumConfig(
        pageLoaderConfig.coroutineScope,
        pageLoaderConfig.processingDispatcher
    )

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataChangedCallbacks.add(callback)
        if (isAnyDataChanged) config.coroutineScope.launch {
            setDataMutex.withLock {
                resendAllPages(listOf(callback))
            }
        }
    }

    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>): Boolean {
        return dataChangedCallbacks.remove(callback)
    }

    fun removeDataSourcePages(dataSourceIndex: Int) {
        _dataPages.removeAll { it.dataSourceIndex == dataSourceIndex }
        updateIndexes()
    }

    fun movePages(fromDataSourceIndex: Int, toDataSourceIndex: Int) {
        val fromPages = _dataPages.filter { it.dataSourceIndex == fromDataSourceIndex }
        if (fromPages.isEmpty()) return

        val firstFromIndex =
            _dataPages.indexOfFirst { it.dataSourceIndex == fromDataSourceIndex }
        val firstToIndex =
            _dataPages.indexOfLast { it.dataSourceIndex == toDataSourceIndex }.coerceAtLeast(0)
        repeat(fromPages.size) { _dataPages.removeAt(firstFromIndex) }
        _dataPages.addAll(firstToIndex, fromPages)
        updateIndexes()
    }

    fun updateIndexes() {
        val newCachedData = mutableMapOf<Int, Pair<Key?, PagingParams>>()
        for (index in _dataPages.indices) {
            _dataPages[index].apply {
                val dataSource = dataSourceWithIndex.first
                cachedData[pageIndex]?.let { newCachedData[index] = it }
                pageIndex = index
                dataSourceIndex = dataSourcesManager.getSourceIndex(dataSource)
                dataSourceWithIndex = dataSource to dataSourceIndex

                val previousPage = _dataPages.getOrNull(index - 1)
                val isPreviousPageHaveSameDataSource =
                    previousPage?.dataSourceWithIndex?.first == dataSource
                previousPageKey = if (isPreviousPageHaveSameDataSource) {
                    previousPage?.currentPageKey
                } else null
                val nextPage = _dataPages.getOrNull(index + 1)
                nextPageKey = if (isPreviousPageHaveSameDataSource) {
                    nextPage?.currentPageKey
                } else null
                pageIndexInDataSource = if (isPreviousPageHaveSameDataSource) {
                    previousPage?.pageIndexInDataSource ?.let { it + 1 } ?: 0
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
                        pageIndexInSource = page.pageIndexInDataSource,
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
        toCallbacks.forEach { it.onEvents(events) }
    }

    fun getCachedData(index: Int) = cachedData[index]

    suspend fun savePage(
        newIndex: Int,
        result: LoadResult.Success<Key, Data>,
        page: DataPage<Key, Data>,
        loadParams: LoadParams<Key>,
        shouldReplaceOnConflict: Boolean,
        dataSetCallbackFlow: StateFlow<(() -> Unit)?>,
        onPageRemoved: (inBeginning: Boolean, pageIndex: Int) -> Unit,
        onLastPageNextKeyChanged: suspend (Key?, Boolean) -> Unit,
    ) {
        isAnyDataChanged = true
        result.cachedResult?.let { cachedData[page.pageIndex] = page.currentPageKey to it }
        val isExistingPage = dataPages.getOrNull(newIndex) != null
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        if (isExistingPage && shouldReplaceOnConflict) _dataPages[newIndex] = page
        else _dataPages.add(newIndex.coerceAtLeast(0), page)
        val invalidateData =
            loadParams.pagingParams?.getOrNull(PagingLibraryParamsKeys.InvalidateData) ?: false
        collectPageData(
            page = page,
            dataFlow = result.dataFlow,
            invalidateData = invalidateData,
            isExistingPage = isExistingPage,
            isPaginationDown = isPaginationDown,
            dataSetCallbackFlow = dataSetCallbackFlow,
            onPageRemoved = onPageRemoved,
            onNextKeyChanged = onLastPageNextKeyChanged,
        )
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
        callDataChangedCallbacks { InvalidateEvent(invalidateBehavior) }
    }

    private suspend inline fun collectPageData(
        page: DataPage<Key, Data>,
        dataFlow: Flow<UpdatableData<Key, Data>>?,
        invalidateData: Boolean,
        isExistingPage: Boolean,
        isPaginationDown: Boolean,
        dataSetCallbackFlow: StateFlow<(() -> Unit)?>,
        crossinline onPageRemoved: (inBeginning: Boolean, pageIndex: Int) -> Unit,
        crossinline onNextKeyChanged: suspend (Key?, Boolean) -> Unit,
    ) {
        var isFirst = true
        lastPaginationDirection = isPaginationDown

        page.isNullified = false
        pageLoaderConfig.coroutineScope.launch(
            pageLoaderConfig.processingDispatcher + page.listenJob
        ) {
            dataFlow?.collect { value ->
                setDataMutex.withLock {
                    setNewPageData(
                        page = page,
                        value = value,
                        invalidateData = invalidateData,
                        isExistingPage = isExistingPage,
                        dataSetCallbackFlow = dataSetCallbackFlow,
                        onPageRemoved = onPageRemoved,
                        onNextKeyChanged = onNextKeyChanged,
                        isFirst = isFirst,
                        isPaginationDown = isPaginationDown
                    )
                    isFirst = false
                }
            }
        }
    }

    private suspend inline fun setNewPageData(
        page: DataPage<Key, Data>,
        value: UpdatableData<Key, Data>,
        invalidateData: Boolean,
        isExistingPage: Boolean, dataSetCallbackFlow: StateFlow<(() -> Unit)?>,
        crossinline onPageRemoved: (inBeginning: Boolean, pageIndex: Int) -> Unit,
        crossinline onNextKeyChanged: suspend (Key?, Boolean) -> Unit,
        isFirst: Boolean,
        isPaginationDown: Boolean
    ) {
        page.data = value
        val trimEvents = trimPages(lastPaginationDirection)
        val currentKey = page.currentPageKey
        val event = if (!isFirst) {
            PageChangedEvent(
                key = currentKey,
                sourceIndex = page.dataSourceIndex,
                pageIndex = page.pageIndex,
                pageIndexInSource = page.pageIndexInDataSource,
                items = value.data,
                params = value.params,
            )
        } else {

            if (invalidateData) invalidate(skipPage = page)
            if (isExistingPage) PageChangedEvent(
                key = currentKey,
                sourceIndex = page.dataSourceIndex,
                pageIndexInSource = page.pageIndexInDataSource,
                pageIndex = page.pageIndex,
                items = value.data,
                changeType = PageChangedEvent.ChangeType.CHANGE_FROM_NULLS_TO_ITEMS,
                params = value.params
            ) else {
                PageAddedEvent(
                    key = currentKey,
                    sourceIndex = page.dataSourceIndex,
                    pageIndex = page.pageIndex,
                    pageIndexInSource = page.pageIndexInDataSource,
                    items = value.data,
                    params = value.params
                )
            }
        }
        val shouldAwaitFirst = dataSetCallbackFlow.value != null && isFirst
        val awaitEvent: AwaitDataSetEvent<Key, Data>? = if (shouldAwaitFirst) {
            AwaitDataSetEvent {
                dataSetCallbackFlow.value?.invoke()
            }
        } else null
        if (trimEvents.isNullOrEmpty()) {
            if (awaitEvent != null) {
                dataChangedCallbacks.forEach {
                    it.onEvents(listOf(awaitEvent, event))
                }
            } else {
                dataChangedCallbacks.forEach { it.onEvent(event) }
            }
        } else {
            val indexes = buildSet(trimEvents.size) {
                for (trimEvent in trimEvents) {
                    when (trimEvent) {
                        is PageRemovedEvent -> {
                            onPageRemoved(isPaginationDown, trimEvent.pageIndex)
                            add(trimEvent.pageIndex)
                        }

                        is PageChangedEvent -> {
                            onPageRemoved(isPaginationDown, trimEvent.pageIndex)
                            add(trimEvent.pageIndex)
                        }
                    }
                }
            }
            val shouldSendEvent = !indexes.contains(page.pageIndex)

            val events = ArrayList<DataChangedEvent<Key, Data>>(
                trimEvents.size + if (shouldSendEvent) 1 else 0
                        + if (awaitEvent == null) 0 else 1
            )
            if (awaitEvent != null) events.add(awaitEvent)
            if (shouldSendEvent) events.add(event)
            events.addAll(trimEvents)
            if (dataChangedCallbacks.isEmpty()) awaitEvent?.callback?.invoke()
            else dataChangedCallbacks.forEach { it.onEvents(events) }
        }
        val isLastPageChanged: Boolean
        if (isPaginationDown) {
            isLastPageChanged = page == dataPages.lastOrNull()
            page.nextPageKey = value.nextPageKey
        } else {
            isLastPageChanged = page == dataPages.firstOrNull { it.data != null }
            page.previousPageKey = value.nextPageKey
        }
        if (isLastPageChanged) onNextKeyChanged(value.nextPageKey, isPaginationDown)
    }

    /**
     * Removes pages that not needed anymore
     * Also can replace removed pages to null pages if [MaxItemsConfiguration.enableDroppedPagesNullPlaceholders] is true
     */
    private fun trimPages(isPaginationDown: Boolean): List<DataChangedEvent<Key, Data>>? {
        val trimConfig = pageLoaderConfig.maxItemsConfiguration ?: return null
        val maxItemsCount = trimConfig.maxItemsCount.takeIf { it != 0 } ?: return null
        val itemsCount = dataPages.sumOf { it.data?.data?.size ?: 0 }
        if (itemsCount > maxItemsCount) {

            // заменить на удаляемую страницу
            val pageIndex = (if (isPaginationDown) dataPages.indexOfFirst { it.data != null }
            else dataPages.lastIndex)
            val page = dataPages.getOrNull(pageIndex) ?: return null

            // TODO поменять расчёт индекса для удаления кэша
            val maxCachedResultPagesCount = trimConfig.maxCachedResultPagesCount
            if (maxCachedResultPagesCount != null) {
                val pageAbsoluteIndex = page.pageIndex
                val removeCacheIndex = if (isPaginationDown) {
                    pageAbsoluteIndex - maxCachedResultPagesCount
                } else {
                    pageAbsoluteIndex + maxCachedResultPagesCount
                }
                cachedData.remove(removeCacheIndex)
            }

            if (trimConfig.enableDroppedPagesNullPlaceholders && isPaginationDown) {
                val lastData = page.data?.data ?: return null
                val lastDataSize = lastData.size
                page.listenJob.cancel()
                page.isNullified = true
                page.data = null
                return listOf(
                    PageChangedEvent(
                        key = page.currentPageKey,
                        sourceIndex = page.dataSourceIndex,
                        pageIndex = page.pageIndex,
                        pageIndexInSource = page.pageIndexInDataSource,
                        items = buildList(lastDataSize) {
                            repeat(lastDataSize) {
                                add(null)
                            }
                        },
                        changeType = PageChangedEvent.ChangeType.CHANGE_TO_NULLS
                    )
                )
            } else {
                page.listenJob.cancel()
                _dataPages.removeAt(pageIndex)

                val trimEvent = PageRemovedEvent<Key, Data>(
                    key = page.currentPageKey,
                    sourceIndex = page.dataSourceIndex,
                    pageIndex = page.pageIndex,
                    pageIndexInSource = page.pageIndexInDataSource,
                    itemsCount = page.data?.data?.size ?: 0
                )
                page.data = null
                page.isNullified = true
                val anotherEvents = trimPages(isPaginationDown)

                return if (anotherEvents.isNullOrEmpty()) listOf(trimEvent)
                else buildList(1 + anotherEvents.size) {
                    addAll(anotherEvents)
                    add(trimEvent)
                }
            }
        }
        return null
    }

    private suspend inline fun callDataChangedCallbacks(block: () -> DataChangedEvent<Key, Data>?) {
        val event = block() ?: return
        dataChangedCallbacks.forEach { it.onEvent(event) }
    }
}

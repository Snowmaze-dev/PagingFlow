package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.params.DefaultKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.sources.ConcatDataSourceConfig

internal class DataPagesManager<Key : Any, Data : Any, SourcePagingStatus : Any>(
    private val concatDataSourceConfig: ConcatDataSourceConfig<Key>,
    private val setDataMutex: Mutex,
) : PagingDataChangesMedium<Key, Data> {

    private val _dataPages = mutableListOf<DataPage<Key, Data, SourcePagingStatus>>()
    val dataPages: List<DataPage<Key, Data, SourcePagingStatus>> get() = _dataPages

    private val cachedData = mutableMapOf<Int, Pair<Key?, PagingParams>>()
    val currentPagesCount get() = dataPages.size

    private var isNeedToTrim = false

    private val dataChangedCallbacks = mutableListOf<DataChangedCallback<Key, Data>>()

    override val config = DataChangesMediumConfig(
        concatDataSourceConfig.coroutineScope,
        concatDataSourceConfig.processingDispatcher
    )

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataChangedCallbacks.add(callback)
    }

    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataChangedCallbacks.remove(callback)
    }

    fun getCachedData(index: Int) = cachedData[index]

    fun savePage(
        newIndex: Int,
        result: LoadResult.Success<Key, Data, SourcePagingStatus>,
        page: DataPage<Key, Data, SourcePagingStatus>,
        loadParams: LoadParams<Key>,
        onLastPageNextKeyChanged: suspend (Key?, Boolean) -> Unit
    ) {
        if (concatDataSourceConfig.maxItemsCount != null) isNeedToTrim = true
        result.cachedResult?.let { cachedData[page.pageIndex] = page.currentPageKey to it }
        val isExistingPage = dataPages.getOrNull(newIndex) != null
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        if (isExistingPage) {
            _dataPages[newIndex] = page
        } else {
            if (isPaginationDown) _dataPages.add(page)
            else _dataPages.add(0, page)
        }
        val invalidateData = loadParams.pagingParams?.getOrNull(DefaultKeys.InvalidateData) ?: false
        collectPageData(
            page = page,
            result = result,
            invalidateData = invalidateData,
            isExistingPage = isExistingPage,
            isPaginationDown = isPaginationDown,
            onNextKeyChanged = onLastPageNextKeyChanged
        )
    }

    fun invalidate(
        skipPage: DataPage<Key, Data, SourcePagingStatus>? = null,
        removeCachedData: Boolean = true,
    ) {
        for (page in dataPages) {
            if (page == skipPage) continue
            page.listenJob.cancel()
        }
        _dataPages.clear()
        skipPage?.let { _dataPages.add(it) }
        if (removeCachedData) cachedData.clear()
        callDataChangedCallbacks { InvalidateEvent() }
    }

    private fun collectPageData(
        page: DataPage<Key, Data, SourcePagingStatus>,
        result: LoadResult.Success<Key, Data, SourcePagingStatus>,
        invalidateData: Boolean,
        isExistingPage: Boolean,
        isPaginationDown: Boolean,
        onNextKeyChanged: suspend (Key?, Boolean) -> Unit
    ) {
        val coroutineContext = concatDataSourceConfig.processingDispatcher + page.listenJob
        var isValueSet = false
        val currentKey = page.currentPageKey
        concatDataSourceConfig.coroutineScope.launch(coroutineContext) {
            result.dataFlow?.collect { value ->
                page.dataFlow?.value = value
                if (page.dataFlow?.value == null) return@collect
                if (isValueSet) {
                    setDataMutex.withLock {
                        callDataChangedCallbacks {
                            PageChangedEvent(
                                key = currentKey,
                                pageIndex = page.pageIndex,
                                items = value.data,
                                sourceIndex = page.dataSourceIndex
                            )
                        }
                    }
                } else {
                    isValueSet = true
                    if (invalidateData) invalidate(page)
                    setDataMutex.withLock {
                        val trimEvent = trimPages(isPaginationDown)
                        val pageAddEvent = if (isExistingPage) PageChangedEvent(
                            key = currentKey,
                            pageIndex = page.pageIndex,
                            items = value.data,
                            sourceIndex = page.dataSourceIndex,
                            changeType = PageChangedEvent.ChangeType.CHANGE_FROM_NULLS_TO_ITEMS
                        ) else PageAddedEvent(
                            key = currentKey,
                            pageIndex = page.pageIndex,
                            items = value.data,
                            sourceIndex = page.dataSourceIndex
                        )
                        if (trimEvent == null) {
                            callDataChangedCallbacks { pageAddEvent }
                        } else {
                            val events = listOf(trimEvent, pageAddEvent)
                            dataChangedCallbacks.forEach { it.onEvents(events) }
                        }
                    }
                }
                val isLastPageChanged: Boolean
                if (isPaginationDown) {
                    isLastPageChanged = page == dataPages.lastOrNull()
                    page.nextPageKey = value.nextPageKey
                }
                else {
                    isLastPageChanged = page == dataPages.firstOrNull { it.dataFlow != null }
                    page.previousPageKey = value.nextPageKey
                }
                if (isLastPageChanged) onNextKeyChanged(value.nextPageKey, isPaginationDown)
            }
        }
    }

    /**
     * Removes pages that not needed anymore
     * Also can replace removed pages to null pages if [ConcatDataSourceConfig.shouldFillRemovedPagesWithNulls] is true
     */
    private fun trimPages(isPaginationDown: Boolean): DataChangedEvent<Key, Data>? {
        if (!isNeedToTrim) return null
        isNeedToTrim = false
        val maxItemsCount = concatDataSourceConfig.maxItemsCount.takeIf { it != 0 } ?: return null
        if (dataPages.sumOf { it.dataFlow?.value?.data?.size ?: 0 } > maxItemsCount) {

            // заменить на удаляемую страницу
            val pageIndex = (if (isPaginationDown) dataPages.indexOfFirst { it.dataFlow != null }
            else dataPages.lastIndex)
            val page = dataPages.getOrNull(pageIndex) ?: return null

            // TODO поменять расчёт индекса для удаления кэша
            val maxCachedResultPagesCount = concatDataSourceConfig.maxCachedResultPagesCount
            if (maxCachedResultPagesCount != null) {
                val pageAbsoluteIndex = page.pageIndex
                val removeCacheIndex = if (isPaginationDown) {
                    pageAbsoluteIndex - maxCachedResultPagesCount
                } else {
                    pageAbsoluteIndex + maxCachedResultPagesCount
                }
                cachedData.remove(removeCacheIndex)
            }

            if (concatDataSourceConfig.shouldFillRemovedPagesWithNulls && isPaginationDown) {
                val lastData = page.dataFlow?.value?.data ?: return null
                val lastDataSize = lastData.size
                page.listenJob.cancel()
                page.dataFlow = null
                return PageChangedEvent(
                    key = page.currentPageKey,
                    pageIndex = page.pageIndex,
                    items = buildList(lastDataSize) {
                        repeat(lastDataSize) {
                            add(null)
                        }
                    },
                    sourceIndex = page.dataSourceIndex,
                    changeType = PageChangedEvent.ChangeType.CHANGE_TO_NULLS
                )
            } else {
                page.listenJob.cancel()
                _dataPages.removeAt(pageIndex)
                page.dataFlow?.value?.data?.let {
                    return PageRemovedEvent(
                        key = page.currentPageKey,
                        pageIndex = page.pageIndex,
                        sourceIndex = page.dataSourceIndex,
                        itemsCount = it.size
                    )
                }
                page.dataFlow = null
            }
        }
        return null
    }

    private inline fun callDataChangedCallbacks(block: () -> DataChangedEvent<Key, Data>?) {
        val event = block() ?: return
        dataChangedCallbacks.forEach { it.onEvent(event) }
    }
}
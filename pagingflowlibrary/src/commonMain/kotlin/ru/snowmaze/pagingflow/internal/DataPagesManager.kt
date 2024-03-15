package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.diff.AwaitDataSetEvent
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.params.PagingLibraryKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.sources.ConcatDataSourceConfig
import ru.snowmaze.pagingflow.sources.MaxItemsConfiguration

internal class DataPagesManager<Key : Any, Data : Any, SourcePagingStatus : Any>(
    private val concatDataSourceConfig: ConcatDataSourceConfig<Key>,
    private val setDataMutex: Mutex,
) : PagingDataChangesMedium<Key, Data> {

    private val _dataPages = mutableListOf<DataPage<Key, Data, SourcePagingStatus>>()
    val dataPages get() = _dataPages

    private val cachedData = mutableMapOf<Int, Pair<Key?, PagingParams>>()
    val currentPagesCount get() = dataPages.size

    private var lastPaginationDirection = true

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

    fun removeDataSourcePages(dataSourceIndex: Int) {
        _dataPages.removeAll(_dataPages.filter {
            it.dataSourceIndex == dataSourceIndex
        })
    }

    fun getCachedData(index: Int) = cachedData[index]

    suspend fun savePage(
        newIndex: Int,
        result: LoadResult.Success<Key, Data, SourcePagingStatus>,
        page: DataPage<Key, Data, SourcePagingStatus>,
        loadParams: LoadParams<Key>,
        onLastPageNextKeyChanged: suspend (Key?, Boolean) -> Unit,
    ) {
        result.cachedResult?.let { cachedData[page.pageIndex] = page.currentPageKey to it }
        val isExistingPage = dataPages.getOrNull(newIndex) != null
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        if (isExistingPage) {
            _dataPages[newIndex] = page
        } else {
            if (isPaginationDown) _dataPages.add(page)
            else _dataPages.add(0, page)
        }
        val invalidateData =
            loadParams.pagingParams?.getOrNull(PagingLibraryKeys.InvalidateData) ?: false
        val awaitFirstPageTime =
            if (loadParams.pagingParams?.containsKey(PagingLibraryKeys.AwaitFirstDataSet) == true) {
                loadParams.pagingParams.getOrNull(PagingLibraryKeys.AwaitFirstDataSet) ?: 1000L
            } else null
        collectPageData(
            page = page,
            result = result,
            invalidateData = invalidateData,
            isExistingPage = isExistingPage,
            isPaginationDown = isPaginationDown,
            awaitFirstPageTime = awaitFirstPageTime,
            onNextKeyChanged = onLastPageNextKeyChanged
        )
    }

    suspend fun invalidate(
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

    private suspend inline fun collectPageData(
        page: DataPage<Key, Data, SourcePagingStatus>,
        result: LoadResult.Success<Key, Data, SourcePagingStatus>,
        invalidateData: Boolean,
        isExistingPage: Boolean,
        isPaginationDown: Boolean,
        awaitFirstPageTime: Long?,
        crossinline onNextKeyChanged: suspend (Key?, Boolean) -> Unit
    ) {
        val coroutineContext = concatDataSourceConfig.processingDispatcher + page.listenJob
        var isValueSet = false
        val currentKey = page.currentPageKey
        lastPaginationDirection = isPaginationDown
        var isFirst = true
        var firstDataCallback: (() -> Unit)? = null
        val awaitFirstPageSet = awaitFirstPageTime != null

        concatDataSourceConfig.coroutineScope.launch(coroutineContext) {
            result.dataFlow?.collect { value ->
                setDataMutex.withLock {
                    page.dataFlow?.value = value
                    if (page.dataFlow?.value == null) return@collect
                    val trimEvents = trimPages(lastPaginationDirection)
                    val event = if (isValueSet) {
                        PageChangedEvent(
                            key = currentKey,
                            pageIndex = page.pageIndex,
                            items = value.data,
                            sourceIndex = page.dataSourceIndex,
                            params = value.params
                        )
                    } else {
                        isValueSet = true

                        // TODO обработка AwaitDataLoad
                        if (invalidateData) invalidate(page)
                        if (isExistingPage) PageChangedEvent(
                            key = currentKey,
                            pageIndex = page.pageIndex,
                            items = value.data,
                            sourceIndex = page.dataSourceIndex,
                            changeType = PageChangedEvent.ChangeType.CHANGE_FROM_NULLS_TO_ITEMS,
                            params = value.params
                        ) else PageAddedEvent(
                            key = currentKey,
                            pageIndex = page.pageIndex,
                            items = value.data,
                            sourceIndex = page.dataSourceIndex,
                            params = value.params
                        )
                    }
                    val shouldAwaitFirst = awaitFirstPageSet && isFirst
                    val awaitEvent: AwaitDataSetEvent<Key, Data>? = if (shouldAwaitFirst) {
                        AwaitDataSetEvent { firstDataCallback?.invoke() }
                    } else null
                    isFirst = false
                    if (trimEvents.isNullOrEmpty()) {
                        if (awaitEvent != null) {
                            dataChangedCallbacks.forEach {
                                it.onEvents(listOf(awaitEvent, event))
                            }
                        } else {
                            dataChangedCallbacks.forEach { it.onEvent(event) }
                        }
                    } else {
                        val indexes = buildSet(trimEvents.size ?: 0) {
                            for (trimEvent in trimEvents ?: emptyList()) {
                                when (trimEvent) {
                                    is PageRemovedEvent -> add(trimEvent.pageIndex)
                                    is PageChangedEvent -> add(trimEvent.pageIndex)
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
                        dataChangedCallbacks.forEach { it.onEvents(events) }
                    }
                    val isLastPageChanged: Boolean
                    if (isPaginationDown) {
                        isLastPageChanged = page == dataPages.lastOrNull()
                        page.nextPageKey = value.nextPageKey
                    } else {
                        isLastPageChanged = page == dataPages.firstOrNull { it.dataFlow != null }
                        page.previousPageKey = value.nextPageKey
                    }
                    if (isLastPageChanged) onNextKeyChanged(value.nextPageKey, isPaginationDown)
                }
            }
        }
        if (awaitFirstPageSet) {
            withTimeoutOrNull(awaitFirstPageTime ?: 1000L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    firstDataCallback = {
                        firstDataCallback = null
                        cont.resumeWith(Result.success(Unit))
                    }
                    cont.invokeOnCancellation {
                        firstDataCallback = null
                    }
                }
            }
        }
    }

    /**
     * Removes pages that not needed anymore
     * Also can replace removed pages to null pages if [MaxItemsConfiguration.enableDroppedPagesNullPlaceholders] is true
     */
    private fun trimPages(isPaginationDown: Boolean): List<DataChangedEvent<Key, Data>>? {
        val trimConfig = concatDataSourceConfig.maxItemsConfiguration ?: return null
        val maxItemsCount = trimConfig.maxItemsCount.takeIf { it != 0 } ?: return null
        val itemsCount = dataPages.sumOf { it.dataFlow?.value?.data?.size ?: 0 }
        if (itemsCount > maxItemsCount) {

            // заменить на удаляемую страницу
            val pageIndex = (if (isPaginationDown) dataPages.indexOfFirst { it.dataFlow != null }
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
                val lastData = page.dataFlow?.value?.data ?: return null
                val lastDataSize = lastData.size
                page.listenJob.cancel()
                page.dataFlow = null
                return listOf(
                    PageChangedEvent(
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
                )
            } else {
                page.listenJob.cancel()
                _dataPages.removeAt(pageIndex)
                page.dataFlow = null

                val trimEvent = PageRemovedEvent<Key, Data>(
                    key = page.currentPageKey,
                    pageIndex = page.pageIndex,
                    sourceIndex = page.dataSourceIndex,
                    itemsCount = page.dataFlow?.value?.data?.size ?: 0
                )
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

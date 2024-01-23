package ru.snowmaze.pagingflow.sources

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.PagingStatus
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.errorshandler.DefaultPagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.internal.DataPage
import ru.snowmaze.pagingflow.internal.DataSources
import ru.snowmaze.pagingflow.params.DataKey
import ru.snowmaze.pagingflow.params.DefaultKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.result.simpleResult

class ConcatDataSource<Key : Any, Data : Any, SourcePagingStatus : Any>(
    private val concatDataSourceConfig: ConcatDataSourceConfig<Key>
) : SourcesChainSource<Key, Data, SourcePagingStatus> {

    companion object {
        fun <Key : Any> concatDataSourceResultKey() =
            DataKey<ConcatSourceData<Key>>("concat_data_source_result")
    }

    private val defaultParams = concatDataSourceConfig.defaultParams
    private val setDataMutex = Mutex()
    private val _upPagingStatus =
        MutableStateFlow<PagingStatus<SourcePagingStatus>>(PagingStatus.Success(hasNextPage = true))
    private val _downPagingStatus =
        MutableStateFlow<PagingStatus<SourcePagingStatus>>(PagingStatus.Success(hasNextPage = true))
    val upPagingStatus = _upPagingStatus.asStateFlow()
    val downPagingStatus = _downPagingStatus.asStateFlow()
    val isLoading
        get() = upPagingStatus is PagingStatus.Loading<*> ||
                downPagingStatus is PagingStatus.Loading<*>

    override val pagingUnhandledErrorsHandler =
        DefaultPagingUnhandledErrorsHandler<SourcePagingStatus>()

    private val dataSources = DataSources<Key, Data, SourcePagingStatus>()
    private val dataPages = mutableListOf<DataPage<Key, Data, SourcePagingStatus>>()
    private val cachedData = mutableMapOf<Int, Pair<Key?, PagingParams>>()
    val currentPagesCount get() = dataPages.size

    private var isNeedToTrim = false
    private var lastPaginationDirection: PaginationDirection? = null
    private val coroutineScope = concatDataSourceConfig.coroutineScope
    private val dataChangedCallbacks = mutableListOf<DataChangedCallback<Key, Data>>()

    /**
     * Adds callback which called when data has been changed
     */
    fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataChangedCallbacks.add(callback)
    }

    /**
     * Removes data changed callback
     */
    fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataChangedCallbacks.remove(callback)
    }

    override fun addDataSource(dataSource: DataSource<Key, Data, SourcePagingStatus>) {
        dataSources.addDataSource(dataSource)
    }

    override fun removeDataSource(dataSource: DataSource<Key, Data, SourcePagingStatus>) {
        dataSources.removeDataSource(dataSource)
        coroutineScope.launch {
            invalidate(removeCachedData = true)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun load(
        loadParams: LoadParams<Key>
    ): LoadResult<Key, Data, SourcePagingStatus> = setDataMutex.withLock {
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        if (isPaginationDown) _downPagingStatus.value = PagingStatus.Loading()
        else _upPagingStatus.value = PagingStatus.Loading()
        val paginationDirection = loadParams.paginationDirection
        val lastPageIndex = if (isPaginationDown) dataPages.lastIndex
        else dataPages.indexOfFirst { it.dataFlow != null }

        val lastPage = dataPages.getOrNull(lastPageIndex)
        val nextPageKey = if (isPaginationDown) lastPage?.nextPageKey else lastPage?.previousPageKey
        val newIndex = if (isPaginationDown) lastPageIndex + 1
        else lastPageIndex - 1
        val dataSource = dataSources.findNextDataSource(
            currentDataSource = lastPage?.dataSource,
            isThereKey = nextPageKey != null,
            navigationDirection = paginationDirection
        ) ?: return simpleResult(emptyList())
        val currentKey = nextPageKey ?: if (dataPages.isEmpty()) {
            dataSource.defaultLoadParams?.key ?: loadParams.key ?: defaultParams.key
        } else null
        val pageAbsoluteIndex = if (lastPage == null) 0
        else if (isPaginationDown) lastPage.pageIndex + 1 else lastPage.pageIndex - 1
        var cachedResultPair = cachedData[pageAbsoluteIndex]
        if (cachedResultPair != null) {
            if (cachedResultPair.first != currentKey) cachedResultPair = null
        }
        val nextLoadParams = LoadParams(
            pageSize = dataSource.defaultLoadParams?.pageSize ?: loadParams.pageSize,
            paginationDirection = paginationDirection,
            key = currentKey,
            cachedResult = cachedResultPair?.second ?: loadParams.cachedResult
            ?: defaultLoadParams?.cachedResult,
            pagingParams = loadParams.pagingParams ?: defaultLoadParams?.cachedResult
        )
        val result = try {
            dataSource.load(nextLoadParams)
        } catch (e: Throwable) {
            val errorHandler =
                (dataSource.pagingUnhandledErrorsHandler ?: pagingUnhandledErrorsHandler)
            errorHandler.handle(e)
        }
        val status = when (result) {
            is LoadResult.Success<*, *, *> -> PagingStatus.Success(
                sourcePagingStatus = result.status,
                hasNextPage = result.nextNextPageKey != null
            )

            is LoadResult.Failure -> PagingStatus.Failure(
                sourcePagingStatus = result.status,
                throwable = result.throwable
            )
        }
        if (isPaginationDown) _downPagingStatus.value = status
        else _upPagingStatus.value = status
        if (result is LoadResult.Failure) {

            return result as LoadResult<Key, Data, SourcePagingStatus>
        }
        result as LoadResult.Success<Key, Data, SourcePagingStatus>
        if (concatDataSourceConfig.maxPagesCount != null) {
            isNeedToTrim = true
            lastPaginationDirection = paginationDirection
        }
        val valueStateFlow = MutableStateFlow<UpdatableData<Key, Data>?>(null)
        val listenJob = SupervisorJob()
        val page = DataPage(
            dataFlow = valueStateFlow,
            nextPageKey = if (isPaginationDown) result.nextNextPageKey
            else dataPages.first().currentPageKey,
            dataSource = dataSource,
            previousPageKey = if (isPaginationDown) dataPages.lastOrNull()?.currentPageKey
            else result.nextNextPageKey,
            currentPageKey = currentKey,
            listenJob = listenJob,
            pageIndex = pageAbsoluteIndex,
            dataSourceIndex = newIndex.coerceAtLeast(0)
        )
        result.cachedResult?.let { cachedData[pageAbsoluteIndex] = currentKey to it }
        val isExistingPage = dataPages.getOrNull(newIndex) != null
        if (isExistingPage) {
            dataPages[newIndex] = page
        } else {
            if (isPaginationDown) dataPages.add(page)
            else dataPages.add(0, page)
        }

        val invalidateData = loadParams.pagingParams?.getOrNull(DefaultKeys.InvalidateData) ?: false
        val coroutineContext = concatDataSourceConfig.processingDispatcher + listenJob
        var isValueSet = false
        concatDataSourceConfig.coroutineScope.launch(coroutineContext) {
            result.dataFlow?.collect { value ->
                valueStateFlow.value = value
                if (page.dataFlow?.value == null) return@collect
                if (isValueSet) {
                    setDataMutex.withLock {
                        callDataChangedCallbacks {
                            onPageChanged(
                                key = currentKey,
                                pageIndex = page.pageIndex,
                                items = value.data,
                                sourceIndex = page.dataSourceIndex
                            )
                        }
                    }
                } else {
                    isValueSet = true
                    if (invalidateData) invalidateInternal(page)
                    setDataMutex.withLock {
                        trimPages()
                        callDataChangedCallbacks {
                            if (isExistingPage) onPageChanged(
                                key = currentKey,
                                pageIndex = page.pageIndex,
                                items = value.data,
                                sourceIndex = page.dataSourceIndex
                            ) else onPageAdded(
                                key = currentKey,
                                pageIndex = page.pageIndex,
                                items = value.data,
                                sourceIndex = page.dataSourceIndex
                            )
                        }
                    }
                }
            }
        }
        result.copy(
            dataFlow = result.dataFlow,
            additionalData = PagingParams {
                put(
                    concatDataSourceResultKey(),
                    ConcatSourceData(currentKey, result.additionalData)
                )
            }
        )
    }

    /**
     * Deletes all pages
     * @param removeCachedData should also remove cached data for pages?
     */
    suspend fun invalidate(removeCachedData: Boolean) = setDataMutex.withLock {
        withContext(concatDataSourceConfig.processingDispatcher) {
            invalidateInternal(removeCachedData = removeCachedData)
        }
    }

    private fun invalidateInternal(
        skipPage: DataPage<Key, Data, SourcePagingStatus>? = null,
        removeCachedData: Boolean = true
    ) {
        for (page in dataPages) {
            if (page == skipPage) continue
            page.listenJob.cancel()
        }
        dataPages.clear()
        skipPage?.let { dataPages.add(it) }
        dataChangedCallbacks.forEach { it.onInvalidate() }
        if (removeCachedData) cachedData.clear()
    }

    /**
     * Removes pages that not needed anymore
     * Also can replace removed pages to null pages if [ConcatDataSourceConfig.shouldFillRemovedPagesWithNulls] is true
     */
    private fun trimPages() {
        if (!isNeedToTrim) return
        isNeedToTrim = false
        val lastPaginationDirection = lastPaginationDirection ?: return
        val removePagesOffset = concatDataSourceConfig
            .maxPagesCount.takeIf { it != 0 } ?: return
        if (dataPages.size > removePagesOffset) {
            val isDown = lastPaginationDirection == PaginationDirection.DOWN

            // заменить на удаляемую страницу
            val pageIndex = (if (isDown) dataPages.indexOfFirst { it.dataFlow != null }
            else dataPages.lastIndex)
            val page = dataPages.getOrNull(pageIndex) ?: return

            val maxCachedResultPagesCount = concatDataSourceConfig.maxCachedResultPagesCount
            if (maxCachedResultPagesCount != null) {
                val pageAbsoluteIndex = page.pageIndex
                val removeCacheIndex = if (isDown) pageAbsoluteIndex - maxCachedResultPagesCount
                else pageAbsoluteIndex + maxCachedResultPagesCount
                cachedData.remove(removeCacheIndex)
            }

            if (concatDataSourceConfig.shouldFillRemovedPagesWithNulls && isDown) {
                val lastData = page.dataFlow?.value?.data ?: return
                val lastDataSize = lastData.size
                page.listenJob.cancel()
                page.dataFlow = null
                callDataChangedCallbacks {
                    onPageChanged(
                        key = page.currentPageKey,
                        pageIndex = page.pageIndex,
                        items = buildList(lastDataSize) {
                            repeat(lastDataSize) {
                                add(null)
                            }
                        },
                        sourceIndex = page.dataSourceIndex
                    )
                }
            } else {
                page.listenJob.cancel()
                dataPages.removeAt(pageIndex)
                callDataChangedCallbacks {
                    page.dataFlow?.value?.data?.let {
                        onPageRemoved(
                            key = page.currentPageKey,
                            pageIndex = page.pageIndex,
                            sourceIndex = page.dataSourceIndex
                        )
                    }
                }
                page.dataFlow = null
            }
        }
    }

    private inline fun callDataChangedCallbacks(block: DataChangedCallback<Key, Data>.() -> Unit) {
        dataChangedCallbacks.forEach(block)
    }
}
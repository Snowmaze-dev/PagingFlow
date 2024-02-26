package ru.snowmaze.pagingflow.sources

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.PagingStatus
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.errorshandler.DefaultPagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.internal.DataPage
import ru.snowmaze.pagingflow.internal.DataPagesManager
import ru.snowmaze.pagingflow.internal.DataSources
import ru.snowmaze.pagingflow.params.DataKey
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.result.simpleResult

class ConcatDataSource<Key : Any, Data : Any, SourcePagingStatus : Any>(
    private val concatDataSourceConfig: ConcatDataSourceConfig<Key>,
) : DataSource<Key, Data, SourcePagingStatus>, PagingDataChangesMedium<Key, Data> {

    companion object {
        fun <Key : Any> concatDataSourceResultKey() =
            DataKey<ConcatSourceData<Key>>("concat_data_source_result")
    }

    private val setDataMutex = Mutex()

    private val _upPagingStatus = MutableStateFlow<PagingStatus<SourcePagingStatus>>(
        PagingStatus.Initial()
    )
    private val _downPagingStatus = MutableStateFlow<PagingStatus<SourcePagingStatus>>(
        PagingStatus.Initial()
    )

    val upPagingStatus = _upPagingStatus.asStateFlow()
    val downPagingStatus = _downPagingStatus.asStateFlow()
    val currentPagesCount get() = dataPagesManager.currentPagesCount
    val isLoading
        get() = upPagingStatus is PagingStatus.Loading<*> ||
                downPagingStatus is PagingStatus.Loading<*>

    override val pagingUnhandledErrorsHandler =
        DefaultPagingUnhandledErrorsHandler<SourcePagingStatus>()

    private val dataSources = DataSources<Key, Data, SourcePagingStatus>()

    private val dataPagesManager = DataPagesManager<Key, Data, SourcePagingStatus>(
        concatDataSourceConfig = concatDataSourceConfig,
        setDataMutex = setDataMutex
    )

    override val config = dataPagesManager.config

    init {
        val coroutineScope = CoroutineScope(config.processingDispatcher + SupervisorJob())
        coroutineScope.launch {
            try {
                config.coroutineScope.launch { awaitCancellation() }.join()
            } finally {
                coroutineScope.launch { invalidate(true) }
            }
        }
    }

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataPagesManager.addDataChangedCallback(callback)
    }

    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataPagesManager.removeDataChangedCallback(callback)
    }

    fun addDataSource(dataSource: DataSource<Key, Data, SourcePagingStatus>) {
        dataSources.addDataSource(dataSource)
    }

    fun removeDataSource(dataSource: DataSource<Key, Data, SourcePagingStatus>) {
        dataSources.removeDataSource(dataSource)
        concatDataSourceConfig.coroutineScope.launch {
            invalidate(removeCachedData = true)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun load(
        loadParams: LoadParams<Key>,
    ): LoadResult<Key, Data, SourcePagingStatus> = setDataMutex.withLock {
        val dataPages = dataPagesManager.dataPages
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        if (isPaginationDown) _downPagingStatus.value = PagingStatus.Loading()
        else _upPagingStatus.value = PagingStatus.Loading()
        val (lastPageIndex, lastPage, nextPageKey) = getNextPageKey(isPaginationDown)
        val paginationDirection = loadParams.paginationDirection

        val newIndex = if (isPaginationDown) lastPageIndex + 1
        else lastPageIndex - 1
        val dataSourceWithIndex = dataSources.findNextDataSource(
            currentDataSource = lastPage?.dataSource,
            isThereKey = nextPageKey != null,
            paginationDirection = paginationDirection
        ) ?: return simpleResult(emptyList())
        val dataSource = dataSourceWithIndex.first
        val currentKey = nextPageKey ?: if (dataPages.isEmpty()) {
            dataSource.defaultLoadParams?.key ?: loadParams.key
            ?: concatDataSourceConfig.defaultParamsProvider().key
        } else null
        val pageAbsoluteIndex = if (lastPage == null) 0
        else if (isPaginationDown) lastPage.pageIndex + 1 else lastPage.pageIndex - 1
        var cachedResultPair = dataPagesManager.getCachedData(pageAbsoluteIndex)
        if (cachedResultPair != null) {
            if (cachedResultPair.first != currentKey) cachedResultPair = null
        }
        val nextLoadParams = LoadParams(
            pageSize = dataSource.defaultLoadParams?.pageSize ?: loadParams.pageSize,
            paginationDirection = paginationDirection,
            key = currentKey,
            cachedResult = cachedResultPair?.second ?: loadParams.cachedResult
            ?: defaultLoadParams?.cachedResult,
            pagingParams = loadParams.pagingParams ?: defaultLoadParams?.pagingParams
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
                hasNextPage = result.nextPageKey != null
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
        val listenJob = SupervisorJob()
        val page = DataPage(
            dataFlow = MutableStateFlow(null),
            nextPageKey = if (isPaginationDown) result.nextPageKey
            else dataPages.getOrNull(lastPageIndex)?.currentPageKey,
            dataSource = dataSourceWithIndex,
            previousPageKey = if (isPaginationDown) dataPages.getOrNull(lastPageIndex)?.currentPageKey
            else result.nextPageKey,
            currentPageKey = currentKey,
            listenJob = listenJob,
            pageIndex = pageAbsoluteIndex,
            dataSourceIndex = dataSourceWithIndex.second
        )
        dataPagesManager.savePage(
            newIndex = newIndex,
            result = result,
            page = page,
            loadParams = loadParams
        )
        result.copy(
            dataFlow = result.dataFlow,
            nextPageKey = result.nextPageKey ?: getNextPageKey(isPaginationDown).third,
            additionalData = PagingParams {
                put(
                    concatDataSourceResultKey(),
                    ConcatSourceData(currentKey, result.additionalData)
                )
            }
        )
    }

    private inline fun getNextPageKey(isPaginationDown: Boolean): Triple<Int, DataPage<Key, Data, SourcePagingStatus>?, Key?> {
        val dataPages = dataPagesManager.dataPages
        val lastPageIndex = if (isPaginationDown) dataPages.lastIndex
        else dataPages.indexOfFirst { it.dataFlow != null }

        val lastPage = dataPages.getOrNull(lastPageIndex)
        return Triple(
            first = lastPageIndex,
            second = lastPage,
            third = if (isPaginationDown) lastPage?.nextPageKey else lastPage?.previousPageKey
        )
    }

    /**
     * Deletes all pages
     * @param removeCachedData should also remove cached data for pages?
     */
    suspend fun invalidate(removeCachedData: Boolean) = setDataMutex.withLock {
        withContext(concatDataSourceConfig.processingDispatcher) {
            dataPagesManager.invalidate(removeCachedData = removeCachedData)
        }
    }
}
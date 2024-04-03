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
import ru.snowmaze.pagingflow.utils.DiffOperation

class ConcatDataSource<Key : Any, Data : Any, SourcePagingStatus : Any>(
    private val concatDataSourceConfig: ConcatDataSourceConfig<Key>,
) : DataSource<Key, Data, SourcePagingStatus>, PagingDataChangesMedium<Key, Data> {

    companion object {
        fun <Key : Any> concatDataSourceResultKey() =
            DataKey<ConcatSourceData<Key>>("concat_data_source_result")
    }

    private val setDataMutex = Mutex()

    private val _upPagingStatus = MutableStateFlow<PagingStatus<SourcePagingStatus>>(
        PagingStatus.Initial(hasNextPage = false)
    )
    private val _downPagingStatus = MutableStateFlow<PagingStatus<SourcePagingStatus>>(
        PagingStatus.Initial(hasNextPage = true)
    )
    val firstPageIndex get() = dataPagesManager.dataPages
        .firstOrNull { it.dataFlow != null }?.pageIndex

    val pagesCount get() = dataPagesManager.dataPages.count { it.dataFlow != null }

    val upPagingStatus = _upPagingStatus.asStateFlow()
    val downPagingStatus = _downPagingStatus.asStateFlow()
    val currentPagesCount get() = dataPagesManager.currentPagesCount
    val isLoading
        get() = upPagingStatus is PagingStatus.Loading<*> ||
                downPagingStatus is PagingStatus.Loading<*>

    override val pagingUnhandledErrorsHandler =
        DefaultPagingUnhandledErrorsHandler<SourcePagingStatus>()

    private val dataSourcesManager = DataSources<Key, Data, SourcePagingStatus>()

    private val dataPagesManager = DataPagesManager<Key, Data, SourcePagingStatus>(
        concatDataSourceConfig = concatDataSourceConfig,
        setDataMutex = setDataMutex,
        dataSourcesManager = dataSourcesManager
    )

    override val config = dataPagesManager.config

    init {
        val coroutineScope = CoroutineScope(config.processingDispatcher + SupervisorJob())
        coroutineScope.launch {
            try {
                config.coroutineScope.launch { awaitCancellation() }.join()
            } finally {
                coroutineScope.launch { invalidate(true, isFullInvalidate = true) }
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
        dataSourcesManager.addDataSource(dataSource)
        _downPagingStatus.value = downPagingStatus.value.mapHasNext(true)
    }

    fun removeDataSource(dataSource: DataSource<Key, Data, SourcePagingStatus>) {
        val dataSourceIndex = dataSourcesManager.getSourceIndex(dataSource)
        if (dataSourceIndex == -1) return
        removeDataSource(dataSourceIndex)
    }

    fun removeDataSource(dataSourceIndex: Int) {
        concatDataSourceConfig.coroutineScope.launch {
            setDataMutex.withLock {
                remove(dataSourceIndex)
            }
        }
    }

    private suspend fun insert(item: DataSource<Key, Data, SourcePagingStatus>, index: Int) {
        val lastLoadedDataSource = dataPagesManager.dataPages.maxOfOrNull { it.dataSourceIndex }
        dataSourcesManager.addDataSource(item, index)
        if (index > (lastLoadedDataSource ?: 0)) return
        val previousIndex = (index - 1).coerceAtLeast(0)
        var pageIndex = if (index == 0) -1 else dataPagesManager.dataPages.indexOfLast {
            it.dataSourceIndex == previousIndex
        }
        do {
            val result = loadData(
                loadParams = concatDataSourceConfig.defaultParamsProvider(),
                lastPageIndex = pageIndex,
                shouldReplaceOnConflict = false
            )
            pageIndex++
        } while (result is LoadResult.Success<Key, Data, SourcePagingStatus> && result.nextPageKey != null)
    }

    private fun remove(index: Int) {
        if (dataSourcesManager.removeDataSource(index)) {
            dataPagesManager.removeDataSourcePages(index)
        }
    }

    private fun move(oldIndex: Int, newIndex: Int) {
        val newMaxIndex = newIndex
            .coerceAtMost(dataSourcesManager.dataSources.size - 1)
            .coerceAtLeast(0)
        try {
            dataSourcesManager.moveDataSource(oldIndex, newMaxIndex)
            dataPagesManager.movePages(oldIndex, newMaxIndex)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setDataSources(
        newDataSourceList: List<DataSource<Key, Data, SourcePagingStatus>>,
        diff: (
            oldList: List<DataSource<Key, Data, SourcePagingStatus>>,
            newList: List<DataSource<Key, Data, SourcePagingStatus>>
        ) -> List<DiffOperation<DataSource<Key, Data, SourcePagingStatus>>>
    ) = concatDataSourceConfig.coroutineScope.launch {
        setDataMutex.withLock {
            val dataSources = dataSourcesManager.dataSources
            val operations = diff(dataSources, newDataSourceList)
            if (operations.isEmpty()) return@withLock
            for (operation in operations) {
                when (operation) {
                    is DiffOperation.Remove<*> -> repeat(operation.count) { remove(operation.index) }

                    is DiffOperation.Add<DataSource<Key, Data, SourcePagingStatus>> -> {
                        for (item in (operation.items ?: continue).withIndex()) {
                            insert(item.value, operation.index + item.index)
                        }
                    }

                    is DiffOperation.Move -> move(
                        oldIndex = operation.fromIndex,
                        newIndex = operation.toIndex
                    )
                }
            }
            dataPagesManager.resendAllPages()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun load(
        loadParams: LoadParams<Key>,
    ) = setDataMutex.withLock {
        val dataPages = dataPagesManager.dataPages
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN

        // getting last page and nextPageKey
        val lastPageIndex = if (isPaginationDown) dataPages.lastIndex
        else dataPages.indexOfFirst { it.dataFlow != null }
        loadData(
            loadParams = loadParams,
            lastPageIndex = lastPageIndex,
            shouldReplaceOnConflict = true
        )
    }

    private suspend fun loadData(
        loadParams: LoadParams<Key>,
        lastPageIndex: Int,
        shouldReplaceOnConflict: Boolean
    ): LoadResult<Key, Data, SourcePagingStatus> {
        val dataPages = dataPagesManager.dataPages
        val paginationDirection = loadParams.paginationDirection
        val lastPage = dataPages.getOrNull(lastPageIndex)

        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        val nextPageKey =
            if (isPaginationDown) lastPage?.nextPageKey else lastPage?.previousPageKey

        // finding next data source
        val newAbsoluteIndex = (lastPage?.pageIndex ?: -1) + if (isPaginationDown) 1 else -1
        val dataSourceWithIndex = dataSourcesManager.findNextDataSource(
            currentDataSource = lastPage?.dataSourceWithIndex,
            isThereKey = nextPageKey != null || newAbsoluteIndex == 0,
            paginationDirection = paginationDirection
        ) ?: return LoadResult.NothingToLoad()

        // setting status that we loading
        if (isPaginationDown) _downPagingStatus.value = PagingStatus.Loading()
        else _upPagingStatus.value = PagingStatus.Loading()

        // picking currentKey and getting cache in case it was saved earlier
        val dataSource = dataSourceWithIndex.first
        val currentKey = nextPageKey ?: if (dataPages.isEmpty()) {
            dataSource.defaultLoadParams?.key ?: loadParams.key
            ?: concatDataSourceConfig.defaultParamsProvider().key
        } else null
        val pageAbsoluteIndex = if (lastPage == null) 0
        else if (isPaginationDown) lastPage.pageIndex + 1 else lastPage.pageIndex - 1
        var cachedResultPair = dataPagesManager.getCachedData(pageAbsoluteIndex)

        // if page key changed don't use cache
        // TODO do cache delete in pages manager in case of key change
        if (cachedResultPair != null) {
            if (cachedResultPair.first != currentKey) cachedResultPair = null
        }

        // loading next page of data
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

        // setting new status after loading completed
        val status = when (result) {
            is LoadResult.Success<*, *, *> -> PagingStatus.Success(
                sourcePagingStatus = result.status,
                hasNextPage = result.nextPageKey != null
            )

            is LoadResult.Failure -> PagingStatus.Failure(
                sourcePagingStatus = result.status,
                throwable = result.throwable
            )

            is LoadResult.NothingToLoad -> PagingStatus.Success(
                sourcePagingStatus = result.status,
                hasNextPage = false
            )
        }
        if (isPaginationDown) _downPagingStatus.value = status
        else _upPagingStatus.value = status
        if (result !is LoadResult.Success) {

            return result as LoadResult<Key, Data, SourcePagingStatus>
        }
        result as LoadResult.Success<Key, Data, SourcePagingStatus>

        // saving page to pages manager
        val listenJob = SupervisorJob()
        val previousPageKey = if (isPaginationDown) lastPage?.currentPageKey
        else result.nextPageKey
        val page = DataPage(
            dataFlow = MutableStateFlow(null),
            nextPageKey = if (isPaginationDown) result.nextPageKey
            else lastPage?.currentPageKey,
            dataSourceWithIndex = dataSourceWithIndex,
            previousPageKey = previousPageKey,
            currentPageKey = currentKey,
            listenJob = listenJob,
            pageIndex = pageAbsoluteIndex,
            dataSourceIndex = dataSourceWithIndex.second
        )
        val newIndex = lastPageIndex + if (isPaginationDown) 1 else -1
        dataPagesManager.savePage(
            newIndex = newIndex,
            result = result,
            page = page,
            loadParams = loadParams,
            shouldReplaceOnConflict = shouldReplaceOnConflict,
            onPageRemoved = { inBeginning, pageIndex ->
                changeHasNextStatus(
                    inEnd = !inBeginning,
                    hasNext = true
                )
            },
            onLastPageNextKeyChanged = { newNextKey, isPaginationDown ->
                changeHasNextStatus(
                    inEnd = isPaginationDown,
                    hasNext = newNextKey != null
                )
            }
        )
        if (!shouldReplaceOnConflict) dataPagesManager.updateIndexes()

        // preparing result
        return result.copy(
            dataFlow = result.dataFlow,
            nextPageKey = result.nextPageKey,
            additionalData = PagingParams {
                put(
                    concatDataSourceResultKey(),
                    ConcatSourceData(
                        currentKey,
                        result.additionalData,
                        result.nextPageKey != null || dataSourcesManager.findNextDataSource(
                            currentDataSource = dataSourceWithIndex,
                            isThereKey = false,
                            paginationDirection = paginationDirection
                        ) != null
                    )
                )
            }
        )
    }

    private fun changeHasNextStatus(inEnd: Boolean, hasNext: Boolean) {
        val stateFlow = if (inEnd) _downPagingStatus else _upPagingStatus
        stateFlow.value = stateFlow.value.mapHasNext(hasNext)
    }

    private fun PagingStatus<SourcePagingStatus>.mapHasNext(
        hasNext: Boolean,
    ) = when (this) {
        is PagingStatus.Success -> PagingStatus.Success(sourcePagingStatus, hasNext)
        is PagingStatus.Initial -> PagingStatus.Initial(hasNext)
        else -> this
    }

    /**
     * Deletes all pages
     * @param removeCachedData should also remove cached data for pages?
     */
    suspend fun invalidate(
        removeCachedData: Boolean, isFullInvalidate: Boolean = false
    ) = setDataMutex.withLock {
        withContext(concatDataSourceConfig.processingDispatcher) {
            dataPagesManager.invalidate(
                removeCachedData = removeCachedData,
                isFullInvalidate = isFullInvalidate
            )
        }
    }
}
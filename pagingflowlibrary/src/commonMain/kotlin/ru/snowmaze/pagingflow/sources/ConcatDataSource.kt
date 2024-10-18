package ru.snowmaze.pagingflow.sources

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.PagingStatus
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.errorshandler.DefaultPagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.internal.DataPage
import ru.snowmaze.pagingflow.internal.DataPagesManager
import ru.snowmaze.pagingflow.internal.DataSources
import ru.snowmaze.pagingflow.params.DataKey
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.result.mapParams
import ru.snowmaze.pagingflow.utils.DiffOperation
import ru.snowmaze.pagingflow.utils.toInfo

class ConcatDataSource<Key : Any, Data : Any>(
    private val concatDataSourceConfig: ConcatDataSourceConfig<Key>,
) : DataSource<Key, Data>, PagingDataChangesMedium<Key, Data> {

    companion object {
        fun <Key : Any> concatDataSourceResultKey() =
            DataKey<ConcatSourceData<Key>>("concat_data_source_result")

        private fun <Key : Any, Data : Any> sourceResultKey() =
            DataKey<LoadResult<Key, Data>>("source_result_key")
    }

    private val loadPageMutex = Mutex()

    private val _upPagingStatus = MutableStateFlow<PagingStatus>(
        PagingStatus.Initial(hasNextPage = false)
    )
    private val _downPagingStatus = MutableStateFlow<PagingStatus>(
        PagingStatus.Initial(hasNextPage = true)
    )
    val firstPageInfo
        get() = dataPagesManager.dataPages.firstOrNull { it.dataFlow != null }?.toInfo()
    val lastPageInfo get() = dataPagesManager.dataPages.lastOrNull()?.toInfo()
    val pagesInfo get() = dataPagesManager.dataPages.map { it.toInfo() }

    val pagesCount get() = dataPagesManager.dataPages.count { it.dataFlow != null }
    val concatSourceResultKey = concatDataSourceResultKey<Key>()
    private val sourceResultKey = sourceResultKey<Key, Data>()

    val upPagingStatus = _upPagingStatus.asStateFlow()
    val downPagingStatus = _downPagingStatus.asStateFlow()
    val currentPagesCount get() = dataPagesManager.currentPagesCount
    val isLoading
        get() = upPagingStatus.value is PagingStatus.Loading ||
                downPagingStatus.value is PagingStatus.Loading

    override val pagingUnhandledErrorsHandler =
        DefaultPagingUnhandledErrorsHandler()

    private val dataSourcesManager = DataSources<Key, Data>()

    private val dataPagesManager = DataPagesManager(
        concatDataSourceConfig = concatDataSourceConfig,
        setDataMutex = loadPageMutex,
        dataSourcesManager = dataSourcesManager
    )

    override val config = dataPagesManager.config

    init {
        val coroutineScope = CoroutineScope(config.processingDispatcher + SupervisorJob())
        coroutineScope.launch {
            try {
                config.coroutineScope.launch { awaitCancellation() }.join()
            } finally {
                invalidate(
                    invalidateBehavior = InvalidateBehavior.INVALIDATE_IMMEDIATELY,
                    removeCachedData = true
                )
            }
        }
    }

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataPagesManager.addDataChangedCallback(callback)
    }

    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>): Boolean {
        return dataPagesManager.removeDataChangedCallback(callback)
    }

    fun addDataSource(dataSource: DataSource<Key, Data>) {
        dataSourcesManager.addDataSource(dataSource)
        _downPagingStatus.value = downPagingStatus.value.mapHasNext(true)
    }

    fun removeDataSource(dataSource: DataSource<Key, Data>) {
        val dataSourceIndex = dataSourcesManager.getSourceIndex(dataSource)
        if (dataSourceIndex == -1) return
        removeDataSource(dataSourceIndex)
    }

    fun removeDataSource(dataSourceIndex: Int) {
        concatDataSourceConfig.coroutineScope.launch {
            loadPageMutex.withLock {
                remove(dataSourceIndex)
            }
        }
    }

    suspend fun invalidateAndSetDataSources(dataSourceList: List<DataSource<Key, Data>>) {
        loadPageMutex.withLock {
            withContext(concatDataSourceConfig.processingDispatcher) {
                dataPagesManager.invalidate(removeCachedData = true)
                dataSourcesManager.replaceDataSources(dataSourceList)
            }
        }
    }

    private suspend fun insert(item: DataSource<Key, Data>, index: Int) {
        val lastLoadedDataSource = dataPagesManager.dataPages.maxOfOrNull { it.dataSourceIndex }
        dataSourcesManager.addDataSource(item, index)
        if (index > (lastLoadedDataSource ?: 0) || pagesCount == 0) return
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
        } while (result is LoadResult.Success<Key, Data> && result.nextPageKey != null)
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
        } catch (e: Exception) { }
    }

    suspend fun setDataSources(
        newDataSourceList: List<DataSource<Key, Data>>,
        diff: (
            oldList: List<DataSource<Key, Data>>,
            newList: List<DataSource<Key, Data>>
        ) -> List<DiffOperation<DataSource<Key, Data>>>
    ) = loadPageMutex.withLock {
        val dataSources = dataSourcesManager.dataSources
        val operations = diff(dataSources, newDataSourceList)
        if (operations.isEmpty()) return@withLock
        for (operation in operations) {
            when (operation) {
                is DiffOperation.Remove<*> -> repeat(operation.count) { remove(operation.index) }

                is DiffOperation.Add<DataSource<Key, Data>> -> {
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

    @Suppress("UNCHECKED_CAST")
    override suspend fun load(
        loadParams: LoadParams<Key>,
    ) = loadPageMutex.withLock {
        val dataPages = dataPagesManager.dataPages
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN

        val lastPageIndex = {

            // getting last page and nextPageKey
            if (isPaginationDown) dataPages.lastIndex
            else dataPages.indexOfFirst { it.dataFlow != null }
        }

        val loadSeveralPages = loadParams.pagingParams
            ?.getOrNull(PagingLibraryParamsKeys.LoadSeveralPages)

        if (loadSeveralPages != null) {
            var lastResult: LoadResult<Key, Data>? = null
            val resultPagingParams = mutableListOf<PagingParams?>()
            while (true) {
                val currentResult = lastResult?.returnData?.get(sourceResultKey) ?: lastResult
                val currentLoadParams = loadSeveralPages.getPagingParams(
                    currentResult as? LoadResult<Any, Any>
                ) ?: break
                lastResult = loadData(
                    loadParams = loadParams.copy(pagingParams = currentLoadParams),
                    lastPageIndex = lastPageIndex(),
                    shouldReplaceOnConflict = true
                )
                resultPagingParams += lastResult.returnData
                if (lastResult is LoadResult.NothingToLoad) break
                else if (lastResult is LoadResult.Failure) continue
                loadSeveralPages.onSuccess?.invoke(
                    lastResult.returnData?.get(sourceResultKey) as LoadResult.Success<Any, Any>
                )
            }
            lastResult ?: throw IllegalArgumentException("Should load at least one pagination.")
            lastResult = lastResult.mapParams(
                lastResult.returnData?.let { PagingParams(it) } ?: PagingParams()
            )
            val returnData = lastResult.returnData
            returnData?.getOrNull(concatSourceResultKey)?.returnData?.let { PagingParams(it) }?.let {
                returnData.put(concatSourceResultKey, returnData[concatSourceResultKey].copy(
                    returnData = it
                ))
            }
            (returnData?.getOrNull(concatSourceResultKey)?.returnData ?: returnData)?.put(
                ReturnPagingLibraryKeys.PagingParamsList,
                resultPagingParams.map {
                    it?.getOrNull(concatSourceResultKey)?.returnData ?: it
                }
            )
            return@withLock lastResult
        } else loadData(
            loadParams = loadParams,
            lastPageIndex = lastPageIndex(),
            shouldReplaceOnConflict = true
        )
    }

    private suspend fun loadData(
        loadParams: LoadParams<Key>,
        lastPageIndex: Int,
        shouldReplaceOnConflict: Boolean
    ): LoadResult<Key, Data> {
        val dataPages = dataPagesManager.dataPages
        val paginationDirection = loadParams.paginationDirection
        val lastPage = dataPages.getOrNull(lastPageIndex)
        // TODO maybe invalidate here

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
            is LoadResult.Success<*, *> -> PagingStatus.Success(
                hasNextPage = result.nextPageKey != null || dataSourcesManager.findNextDataSource(
                    currentDataSource = dataSourceWithIndex,
                    isThereKey = false,
                    paginationDirection = paginationDirection
                ) != null
            )

            is LoadResult.Failure -> PagingStatus.Failure(
                throwable = result.throwable
            )

            is LoadResult.NothingToLoad -> PagingStatus.Success(
                hasNextPage = false
            )
        }
        if (isPaginationDown) _downPagingStatus.value = status
        else _upPagingStatus.value = status
        if (result !is LoadResult.Success) {

            return result as LoadResult<Key, Data>
        }
        result as LoadResult.Success<Key, Data>

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
        val shouldReturnAwaitJob =
            loadParams.pagingParams?.getOrNull(PagingLibraryParamsKeys.ReturnAwaitJob) == true
        val dataSetCallbackFlow = MutableStateFlow<(() -> Unit)?>(null)
        var job: Job? = null
        if (shouldReturnAwaitJob) {
            job = config.coroutineScope.launch {
                withTimeoutOrNull(10000) {
                    suspendCancellableCoroutine { cont ->
                        val callback = {
                            cont.resumeWith(Result.success(Unit))
                        }
                        dataSetCallbackFlow.value = callback
                        cont.invokeOnCancellation {
                            dataSetCallbackFlow.value = null
                        }
                    }
                }
            }
            dataSetCallbackFlow.first { it != null }
        }

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
            dataSetCallbackFlow = dataSetCallbackFlow,
            onLastPageNextKeyChanged = { newNextKey, isPaginationDown ->
                changeHasNextStatus(
                    inEnd = isPaginationDown,
                    hasNext = newNextKey != null || dataSourcesManager.findNextDataSource(
                        currentDataSource = dataSourceWithIndex,
                        isThereKey = false,
                        paginationDirection = paginationDirection
                    ) != null
                )
            }
        ) {
            if (!shouldReplaceOnConflict) dataPagesManager.updateIndexes()
        }

        val resultData = result.returnData ?: if (job != null) PagingParams() else null
        job?.let { resultData?.put(ReturnPagingLibraryKeys.DataSetJob, job) }

        // preparing result
        return result.copy(
            dataFlow = result.dataFlow,
            nextPageKey = result.nextPageKey,
            returnData = PagingParams {
                put(
                    concatDataSourceResultKey(),
                    ConcatSourceData(
                        currentKey = currentKey,
                        returnData = resultData,
                        hasNext = status.hasNextPage
                    )
                )
                put(sourceResultKey, result)
            }
        )
    }

    private fun changeHasNextStatus(inEnd: Boolean, hasNext: Boolean) {
        val stateFlow = if (inEnd) _downPagingStatus else _upPagingStatus
        stateFlow.value = stateFlow.value.mapHasNext(hasNext)
    }

    private fun PagingStatus.mapHasNext(
        hasNext: Boolean,
    ) = when (this) {
        is PagingStatus.Success -> PagingStatus.Success(hasNext)
        is PagingStatus.Initial -> PagingStatus.Initial(hasNext)
        else -> this
    }

    /**
     * Deletes all pages
     * @param removeCachedData should also remove cached data for pages?
     * @param invalidateBehavior see [InvalidateBehavior]
     */
    suspend fun invalidate(
        invalidateBehavior: InvalidateBehavior?,
        removeCachedData: Boolean
    ) = loadPageMutex.withLock {
        withContext(concatDataSourceConfig.processingDispatcher) {
            dataPagesManager.invalidate(
                invalidateBehavior = invalidateBehavior,
                removeCachedData = removeCachedData,
            )
        }
    }
}
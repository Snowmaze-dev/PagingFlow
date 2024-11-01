package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.PagingStatus
import ru.snowmaze.pagingflow.errorshandler.PagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.params.SourceKeys.pageLoaderResult
import ru.snowmaze.pagingflow.params.SourceKeys.sourceResultKey
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.sources.PageLoaderConfig
import ru.snowmaze.pagingflow.sources.ConcatSourceData
import ru.snowmaze.pagingflow.utils.mapHasNext

internal class PageLoader<Key : Any, Data : Any> constructor(
    private val dataSourcesManager: DataSourcesManager<Key, Data>,
    private val dataPagesManager: DataPagesManager<Key, Data>,
    val pageLoaderConfig: PageLoaderConfig<Key>,
    private val pagingUnhandledErrorsHandler: PagingUnhandledErrorsHandler,
    private val defaultLoadParams: LoadParams<Key>?
) {

    val pageLoaderResultKey = pageLoaderResult<Key>()
    val sourceResultKey = sourceResultKey<Key, Data>()

    val downPagingStatus = MutableStateFlow<PagingStatus>(
        PagingStatus.Initial(hasNextPage = true)
    )

    val upPagingStatus = MutableStateFlow<PagingStatus>(
        PagingStatus.Initial(hasNextPage = false)
    )

    suspend fun loadData(
        loadParams: LoadParams<Key>,
        lastPageIndex: Int,
        shouldReplaceOnConflict: Boolean
    ): LoadResult<Key, Data> {
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
        if (isPaginationDown) downPagingStatus.value = PagingStatus.Loading()
        else upPagingStatus.value = PagingStatus.Loading()

        // picking currentKey and getting cache in case it was saved earlier
        val dataSource = dataSourceWithIndex.first
        val currentKey = nextPageKey ?: if (dataPages.isEmpty()) {
            dataSource.defaultLoadParams?.key ?: loadParams.key
            ?: pageLoaderConfig.defaultParamsProvider().key
        } else null
        val pageAbsoluteIndex = if (lastPage == null) 0
        else if (isPaginationDown) lastPage.pageIndex + 1 else lastPage.pageIndex - 1

        val pageIndexInDataSource = if (lastPage?.dataSourceIndex == dataSourceWithIndex.second) {
            lastPage.pageIndexInDataSource + 1
        } else 0

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
        if (isPaginationDown) downPagingStatus.value = status
        else upPagingStatus.value = status
        if (result !is LoadResult.Success) {

            return result as LoadResult<Key, Data>
        }
        result as LoadResult.Success<Key, Data>

        // saving page to pages manager
        val listenJob = SupervisorJob()
        val previousPageKey = if (isPaginationDown) lastPage?.currentPageKey
        else result.nextPageKey
        val page = DataPage(
            data = null,
            isNullified = false,
            nextPageKey = if (isPaginationDown) result.nextPageKey
            else lastPage?.currentPageKey,
            dataSourceWithIndex = dataSourceWithIndex,
            previousPageKey = previousPageKey,
            currentPageKey = currentKey,
            listenJob = listenJob,
            pageIndex = pageAbsoluteIndex,
            dataSourceIndex = dataSourceWithIndex.second,
            pageIndexInDataSource = pageIndexInDataSource
        )

        val newIndex = lastPageIndex + if (isPaginationDown) 1 else -1
        val shouldReturnAwaitJob =
            loadParams.pagingParams?.getOrNull(PagingLibraryParamsKeys.ReturnAwaitJob) == true
        val dataSetCallbackFlow = MutableStateFlow<(() -> Unit)?>(null)
        var job: Job? = null
        if (shouldReturnAwaitJob) {
            job = pageLoaderConfig.coroutineScope.launch {
                withTimeoutOrNull(10000) {
                    suspendCancellableCoroutine { cont ->
                        val callback = {
                            cont.resumeWith(Result.success(Unit))
                            dataSetCallbackFlow.value = null
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
        )

        // updating indexes in case when inserted new page in middle of other pages
        if (!shouldReplaceOnConflict) dataPagesManager.updateIndexes()

        val resultData = result.returnData ?: if (job != null) PagingParams() else null
        job?.let { resultData?.put(ReturnPagingLibraryKeys.DataSetJob, job) }

        // preparing result
        return result.copy(
            dataFlow = result.dataFlow,
            nextPageKey = result.nextPageKey,
            returnData = PagingParams {
                put(
                    pageLoaderResult(),
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
        val stateFlow = if (inEnd) downPagingStatus else upPagingStatus
        stateFlow.value = stateFlow.value.mapHasNext(hasNext)
    }
}
package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.PagingStatus
import ru.snowmaze.pagingflow.errorshandler.PagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.params.DataKey
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.params.SourceKeys.pageLoaderResult
import ru.snowmaze.pagingflow.params.SourceKeys.sourceResultKey
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.source.PageLoaderConfig
import ru.snowmaze.pagingflow.source.ConcatSourceData
import ru.snowmaze.pagingflow.utils.mapHasNext

internal class PageLoader<Key : Any, Data : Any>(
    private val pagingSourcesManager: PagingSourcesManager<Key, Data>,
    private val dataPagesManager: DataPagesManager<Key, Data>,
    val pageLoaderConfig: PageLoaderConfig<Key>,
    private val pagingUnhandledErrorsHandler: PagingUnhandledErrorsHandler,
    private val defaultLoadParams: LoadParams<Key>?
) {

    val pageLoaderResultKey = pageLoaderResult<Key>()
    val sourceResultKey = sourceResultKey<Key, Data>()
    val statusKey = DataKey<PagingStatus>("paging_status")

    val downPagingStatus = MutableStateFlow<PagingStatus>(
        PagingStatus.Initial(hasNextPage = true)
    )

    val upPagingStatus = MutableStateFlow<PagingStatus>(
        PagingStatus.Initial(hasNextPage = false)
    )

    private val onPageRemoved = { inBeginning: Boolean, pageIndex: Int ->
        changeHasNextStatus(
            inEnd = !inBeginning,
            hasNext = true
        )
    }

    suspend fun loadData(
        loadParams: LoadParams<Key>,
        lastPageIndex: Int,
        shouldReplaceOnConflict: Boolean,
        shouldSetNewStatus: Boolean
    ): LoadResult<Key, Data> {
        val dataPages = dataPagesManager.dataPages
        val paginationDirection = loadParams.paginationDirection
        val lastPage = dataPages.getOrNull(lastPageIndex)

        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        val nextPageKey =
            if (isPaginationDown) lastPage?.nextPageKey else lastPage?.previousPageKey

        // finding next paging source
        val newAbsoluteIndex = (lastPage?.pageIndex ?: -1) + if (isPaginationDown) 1 else -1
        val dataSourceWithIndex = pagingSourcesManager.findNextPagingSource(
            currentPagingSource = lastPage?.pagingSourceWithIndex,
            isThereKey = nextPageKey != null || newAbsoluteIndex == 0,
            paginationDirection = paginationDirection
        ) ?: return LoadResult.NothingToLoad()

        // setting status that we loading
        val currentStatus = if (isPaginationDown) downPagingStatus
        else upPagingStatus
        currentStatus.value = PagingStatus.Loading

        // picking currentKey and getting cache in case it was saved earlier
        val pagingSource = dataSourceWithIndex.first
        val currentKey = nextPageKey ?: if (dataPages.isEmpty()) {
            pagingSource.defaultLoadParams?.key ?: loadParams.key
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
            pageSize = pagingSource.defaultLoadParams?.pageSize ?: loadParams.pageSize,
            paginationDirection = paginationDirection,
            key = currentKey,
            cachedResult = cachedResultPair?.second ?: loadParams.cachedResult
            ?: defaultLoadParams?.cachedResult,
            pagingParams = loadParams.pagingParams ?: defaultLoadParams?.pagingParams
        )
        val result = try {
            pagingSource.load(nextLoadParams)
        } catch (e: Throwable) {
            val errorHandler =
                (pagingSource.pagingUnhandledErrorsHandler ?: pagingUnhandledErrorsHandler)
            errorHandler.handle(e)
        }

        // setting new status after loading completed
        val status = when (result) {
            is LoadResult.Success<*, *> -> PagingStatus.Success(
                hasNextPage = result.nextPageKey != null || pagingSourcesManager.findNextPagingSource(
                    currentPagingSource = dataSourceWithIndex,
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
        if (shouldSetNewStatus) currentStatus.value = status

        if (result !is LoadResult.Success) {

            return result as LoadResult<Key, Data>
        }
        result as LoadResult.Success<Key, Data>

        // saving page to pages manager
        val previousPageKey = if (isPaginationDown) lastPage?.currentPageKey
        else result.nextPageKey
        val page = DataPage(
            data = null,
            isNullified = false,
            nextPageKey = if (isPaginationDown) result.nextPageKey
            else lastPage?.currentPageKey,
            pagingSourceWithIndex = dataSourceWithIndex,
            previousPageKey = previousPageKey,
            currentPageKey = currentKey,
            listenJob = SupervisorJob(),
            pageIndex = pageAbsoluteIndex,
            dataSourceIndex = dataSourceWithIndex.second,
            pageIndexInDataSource = pageIndexInDataSource
        )

        val newIndex = lastPageIndex + if (isPaginationDown) 1 else -1
        val shouldReturnAwaitJob =
            loadParams.pagingParams?.getOrNull(PagingLibraryParamsKeys.ReturnAwaitJob) == true

        val dataSetChannel = if (shouldReturnAwaitJob) Channel<Unit>(1) else null
        val job: Job? = if (shouldReturnAwaitJob) {
            pageLoaderConfig.coroutineScope.launch {
                withTimeoutOrNull(15000) {
                    dataSetChannel?.receive()
                }
            }
        } else null

        dataPagesManager.setupPage(
            newIndex = newIndex,
            result = result,
            page = page,
            loadParams = loadParams,
            shouldReplaceOnConflict = shouldReplaceOnConflict,
            onPageRemoved = onPageRemoved,
            awaitDataSetChannel = dataSetChannel,
            onLastPageNextKeyChanged = { newNextKey, isPaginationDown ->
                changeHasNextStatus(
                    inEnd = isPaginationDown,
                    hasNext = newNextKey != null || pagingSourcesManager.findNextPagingSource(
                        currentPagingSource = dataSourceWithIndex,
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
            returnData = PagingParams(2 + if (shouldSetNewStatus) 0 else 1) {
                put(
                    pageLoaderResult(),
                    ConcatSourceData(
                        currentKey = currentKey,
                        returnData = resultData,
                        hasNext = status.hasNextPage
                    )
                )
                put(sourceResultKey, result)
                if (!shouldSetNewStatus) put(statusKey, status)
            }
        )
    }

    private fun changeHasNextStatus(inEnd: Boolean, hasNext: Boolean) {
        val stateFlow = if (inEnd) downPagingStatus else upPagingStatus
        stateFlow.value = stateFlow.value.mapHasNext(hasNext)
    }
}
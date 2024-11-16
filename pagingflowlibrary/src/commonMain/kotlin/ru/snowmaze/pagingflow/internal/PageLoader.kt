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
import ru.snowmaze.pagingflow.source.PagingSource
import ru.snowmaze.pagingflow.utils.fastFirstOrNull
import ru.snowmaze.pagingflow.utils.fastIndexOfFirst
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
    val statusKey = DataKey<PagingStatus<Key>>("paging_status")
    private var lastDownDataSourceIndex = 0
    private var lastUpDataSourceIndex = 0

    val downPagingStatus = MutableStateFlow<PagingStatus<Key>>(
        PagingStatus.Initial(hasNextPage = true)
    )

    val upPagingStatus = MutableStateFlow<PagingStatus<Key>>(
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
        lastPageIndex: Int?,
        shouldReplaceOnConflict: Boolean,
        shouldSetNewStatus: Boolean
    ): LoadResult<Key, Data> {
        var loadResult = loadDataInternal(
            loadParams = loadParams,
            lastPageIndex = lastPageIndex,
            shouldReplaceOnConflict = shouldReplaceOnConflict,
            shouldSetNewStatus = shouldSetNewStatus,
            dataSourceIndex = null
        )
        if (loadResult is LoadResult.NothingToLoad && lastPageIndex == null) {
            val isLoadingDown = loadParams.paginationDirection == PaginationDirection.DOWN
            val status = if (isLoadingDown) downPagingStatus else upPagingStatus
            while (loadResult is LoadResult.NothingToLoad &&
                status.value.hasNextPage &&
                status.value !== PagingStatus.Loading
            ) {
                loadResult = loadDataInternal(
                    loadParams = loadParams,
                    lastPageIndex = null,
                    shouldReplaceOnConflict = shouldReplaceOnConflict,
                    shouldSetNewStatus = shouldSetNewStatus,
                    dataSourceIndex = if (isLoadingDown) ++lastDownDataSourceIndex
                    else ++lastUpDataSourceIndex
                )
            }
        }
        return loadResult
    }

    private suspend fun loadDataInternal(
        loadParams: LoadParams<Key>,
        lastPageIndex: Int?,
        shouldReplaceOnConflict: Boolean,
        shouldSetNewStatus: Boolean,
        dataSourceIndex: Int?
    ): LoadResult<Key, Data> {
        val dataPages = dataPagesManager.dataPages
        val paginationDirection = loadParams.paginationDirection
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        val currentLastPageIndex = lastPageIndex ?: if (isPaginationDown) dataPages.lastIndex
        else dataPages.fastIndexOfFirst { !it.isNullified }
        val pagingSourceWithIndex: Pair<PagingSource<Key, Data>, Int>
        val lastPage = dataPages.getOrNull(currentLastPageIndex)
        val nextPageKey: Key?
        if (dataSourceIndex == null) {
            nextPageKey = if (isPaginationDown) lastPage?.nextPageKey else lastPage?.previousPageKey

            // finding next paging source
            val newAbsoluteIndex = (lastPage?.pageIndex ?: 0) + if (isPaginationDown) 1 else -1
            pagingSourceWithIndex = pagingSourcesManager.findNextPagingSource(
                currentPagingSource = lastPage?.pagingSourceWithIndex,
                isThereKey = nextPageKey != null || (newAbsoluteIndex == 0 && lastPage?.pagingSourceWithIndex == null),
                paginationDirection = paginationDirection
            ) ?: return LoadResult.NothingToLoad()
        } else {
            val dataSource = pagingSourcesManager.pagingSources.getOrNull(dataSourceIndex)
            if (dataSource == null) {
                val status = if (isPaginationDown) downPagingStatus else upPagingStatus
                status.value = PagingStatus.Success(
                    hasNextPage = false,
                    currentKey = if (status is PagingStatus.Success<*>) status.currentKey as? Key
                    else null
                )
                return LoadResult.NothingToLoad()
            }
            nextPageKey = null
            pagingSourceWithIndex = dataSource to dataSourceIndex
        }
        if (isPaginationDown) lastDownDataSourceIndex = pagingSourceWithIndex.second
        else lastUpDataSourceIndex = pagingSourceWithIndex.second

        // setting status that we loading
        val currentStatusFlow = if (isPaginationDown) downPagingStatus else upPagingStatus
        currentStatusFlow.value = PagingStatus.Loading as PagingStatus<Key>

        // picking currentKey and getting cache in case it was saved earlier
        val pagingSource = pagingSourceWithIndex.first
        val currentKey = nextPageKey ?: if (dataPages.isEmpty()) {
            pagingSource.defaultLoadParams?.key ?: loadParams.key
            ?: pageLoaderConfig.defaultParamsProvider().key
        } else null
        val pageAbsoluteIndex = if (lastPage == null) if (isPaginationDown) 0 else -1
        else if (isPaginationDown) lastPage.pageIndex + 1 else lastPage.pageIndex - 1

        val pageIndexInDataSource = if (lastPage?.dataSourceIndex == pagingSourceWithIndex.second) {
            lastPage.pageIndexInPagingSource + 1
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
            paginationDirection = if (pageAbsoluteIndex >= 0) paginationDirection else
                if (isPaginationDown) PaginationDirection.UP else PaginationDirection.DOWN,
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
                    currentPagingSource = if (lastPageIndex == null) pagingSourceWithIndex else {
                        getLastSourceWithIndex(dataPages, isPaginationDown)
                    },
                    isThereKey = false,
                    paginationDirection = paginationDirection
                ) != null,
                currentKey = currentKey
            )

            is LoadResult.Failure -> PagingStatus.Failure(
                throwable = result.throwable
            )

            is LoadResult.NothingToLoad -> PagingStatus.Success(
                hasNextPage = pagingSourcesManager.findNextPagingSource(
                    currentPagingSource = getLastSourceWithIndex(dataPages, isPaginationDown),
                    isThereKey = false,
                    paginationDirection = paginationDirection
                ) != null,
                currentKey = currentKey
            )
        }

        if (result !is LoadResult.Success) {
            currentStatusFlow.value = status

            return result as LoadResult<Key, Data>
        }
        result as LoadResult.Success<Key, Data>

        if (shouldSetNewStatus) currentStatusFlow.value = status

        // saving page to pages manager
        val previousPageKey = if (isPaginationDown) lastPage?.currentPageKey else result.nextPageKey
        println("previousPage key $previousPageKey result.nextPageKey ${result.nextPageKey}")
        val page = DataPage(
            data = null,
            itemCount = 0,
            isCancelled = false,
            nextPageKey = if (isPaginationDown) result.nextPageKey
            else lastPage?.currentPageKey,
            pagingSourceWithIndex = pagingSourceWithIndex,
            previousPageKey = previousPageKey,
            currentPageKey = currentKey,
            listenJob = SupervisorJob(),
            pageIndex = pageAbsoluteIndex,
            dataSourceIndex = pagingSourceWithIndex.second,
            pageIndexInPagingSource = pageIndexInDataSource
        )
        println("got page $page")

        val newIndex = currentLastPageIndex + if (isPaginationDown) 1 else -1
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
                        currentPagingSource = pagingSourceWithIndex,
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
                    pageLoaderResultKey,
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

    private inline fun getLastSourceWithIndex(
        dataPages: List<DataPage<Key, Data>>,
        isPaginationDown: Boolean
    ) = (if (isPaginationDown) dataPages.lastOrNull()
    else dataPages.fastFirstOrNull { !it.isNullified })?.pagingSourceWithIndex

    private fun changeHasNextStatus(inEnd: Boolean, hasNext: Boolean) {
        val stateFlow = if (inEnd) downPagingStatus else upPagingStatus
        stateFlow.value = stateFlow.value.mapHasNext(hasNext)
    }
}
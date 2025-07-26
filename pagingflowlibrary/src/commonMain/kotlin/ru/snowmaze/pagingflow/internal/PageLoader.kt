package ru.snowmaze.pagingflow.internal

import androidx.collection.ObjectList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.PagingStatus
import ru.snowmaze.pagingflow.errorshandler.PagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.params.DataKey
import ru.snowmaze.pagingflow.params.MutablePagingParams
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.params.SourceKeys.pageLoaderResult
import ru.snowmaze.pagingflow.params.SourceKeys.sourceResultKey
import ru.snowmaze.pagingflow.params.toMutableParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.result.mapParams
import ru.snowmaze.pagingflow.source.ConcatPagingSourceConfig
import ru.snowmaze.pagingflow.source.ConcatSourceData
import ru.snowmaze.pagingflow.source.PagingSource
import ru.snowmaze.pagingflow.utils.elementAtOrNull
import ru.snowmaze.pagingflow.utils.fastFirstOrNull
import ru.snowmaze.pagingflow.utils.fastIndexOfFirst
import ru.snowmaze.pagingflow.utils.fastIndexOfLast
import ru.snowmaze.pagingflow.utils.mapHasNext

internal class PageLoader<Key : Any, Data : Any>(
    private val pagingSourcesManager: PagingSourcesManager<Key, Data>,
    private val dataPagesManager: DataPagesManager<Key, Data>,
    val pageLoaderConfig: ConcatPagingSourceConfig<Key>,
    private val pagingUnhandledErrorsHandler: PagingUnhandledErrorsHandler<Key, Data>,
    private val defaultLoadParams: LoadParams<Key>?
) {

    val pageLoaderResultKey = pageLoaderResult<Key>()
    val sourceResultKey = sourceResultKey<Key, Data>()
    val statusKey = DataKey<PagingStatus<Key>>("paging_status")
    private var lastDownDataSourceIndex = 0
    private var lastUpDataSourceIndex = 0

    val downPagingStatus = MutableStateFlow<PagingStatus<Key>>(
        PagingStatus.Initial(hasNextPage = false)
    )

    val upPagingStatus = MutableStateFlow<PagingStatus<Key>>(
        PagingStatus.Initial(hasNextPage = false)
    )

    suspend inline fun loadData(loadParams: LoadParams<Key>): LoadResult<Key, Data> {
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN

        val loadSeveralPages = loadParams.pagingParams
            ?.getOrNull(PagingLibraryParamsKeys.LoadSeveralPages)

        return if (loadSeveralPages != null) {
            val sourceResultKey = sourceResultKey
            var lastResult: LoadResult<Key, Data>? = null
            val resultPagingParams = mutableListOf<PagingParams?>()
            val key = pageLoaderResultKey
            val pagingStatus = if (isPaginationDown) downPagingStatus
            else upPagingStatus
            while (true) {
                val currentResult = lastResult?.returnData?.getOrNull(sourceResultKey) ?: lastResult
                val currentPagingParams = loadSeveralPages.getPagingParams(
                    currentResult as? LoadResult<Any, Any>
                ) ?: break
                val defaultPagingParams =
                    pageLoaderConfig.defaultParamsProvider().pagingParams?.toMutableParams()
                defaultPagingParams?.put(currentPagingParams)
                lastResult = loadData(
                    loadParams = loadParams.copy(
                        pagingParams = defaultPagingParams ?: currentPagingParams
                    ),
                    lastPageIndex = null,
                    shouldReplaceOnConflict = true,
                    shouldSetNewStatus = false
                )
                resultPagingParams += lastResult.returnData?.getOrNull(key)?.returnData
                    ?: lastResult.returnData
                val status = pagingStatus.value
                if (lastResult is LoadResult.NothingToLoad && status is PagingStatus.Loading ||
                    (status !is PagingStatus.Loading && !status.hasNextPage)
                ) break
                if (lastResult is LoadResult.Failure) continue
                loadSeveralPages.onSuccess?.invoke(
                    lastResult.returnData?.get(sourceResultKey) as LoadResult.Success<Any, Any>
                )
            }
            lastResult ?: throw IllegalArgumentException("Should load at least one pagination.")
            lastResult.returnData?.get(statusKey)?.let {
                (if (isPaginationDown) downPagingStatus
                else upPagingStatus).value = it
            }
            val returnData = lastResult.returnData?.let {
                MutablePagingParams(it)
            } ?: MutablePagingParams()

            lastResult = lastResult.mapParams(returnData)
            returnData.getOrNull(key)?.returnData?.let { MutablePagingParams(it) }?.let {
                returnData.put(key, returnData[key].copy(returnData = it))
            }
            (returnData.getOrNull(key)?.returnData ?: returnData).put(
                ReturnPagingLibraryKeys.PagingParamsList,
                resultPagingParams
            )
            lastResult
        } else loadData(
            loadParams = loadParams,
            lastPageIndex = null,
            shouldReplaceOnConflict = true,
            shouldSetNewStatus = true
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

        // in case when load returned nothing to load but there's also the next paging source available
        // then we loading from next paging source right away
        if (loadResult is LoadResult.NothingToLoad) {
            val isLoadingDown = loadParams.paginationDirection == PaginationDirection.DOWN
            val status = if (isLoadingDown) downPagingStatus else upPagingStatus
            while (loadResult is LoadResult.NothingToLoad &&
                status.value.hasNextPage &&
                status.value !is PagingStatus.Loading
            ) {
                loadResult = loadDataInternal(
                    loadParams = loadParams,
                    lastPageIndex = null,
                    shouldReplaceOnConflict = shouldReplaceOnConflict,
                    shouldSetNewStatus = shouldSetNewStatus,
                    dataSourceIndex = if (isLoadingDown) ++lastDownDataSourceIndex
                    else --lastUpDataSourceIndex
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
        val isLoadingPageInOrder = lastPageIndex == null
        val paginationDirection = loadParams.paginationDirection
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        val currentStatusFlow = if (isPaginationDown) downPagingStatus else upPagingStatus

        val dataPages = dataPagesManager.dataPages
        val currentLastPageIndex = lastPageIndex ?: if (isPaginationDown) {
            dataPages.indexOfLast { !it.isNullified }
        } else dataPages.indexOfFirst { !it.isNullified }
        val pagingSourceWithIndex: Pair<PagingSource<Key, Data>, Int>
        val lastPage = dataPages.elementAtOrNull(currentLastPageIndex)
        val nextPageKey: Key?

        if (dataSourceIndex == null) {
            nextPageKey = if (isPaginationDown) lastPage?.nextPageKey else lastPage?.previousPageKey

            // finding next paging source
            val newAbsoluteIndex = (lastPage?.pageIndex ?: 0) + if (isPaginationDown) 1 else -1
            pagingSourceWithIndex = pagingSourcesManager.findNextPagingSource(
                currentPagingSource = lastPage?.pagingSourceWithIndex,
                isThereKey = nextPageKey != null ||
                        (newAbsoluteIndex == 0 && ((lastPage?.pageIndex ?: 0) >= 0)), // TODO
                paginationDirection = paginationDirection
            ) ?: run {
                currentStatusFlow.value = getSuccessStatus(
                    isLoadingPageInOrder = isLoadingPageInOrder,
                    pagingSourceWithIndex = lastPage?.pagingSourceWithIndex,
                    currentKey = lastPage?.currentPageKey,
                    dataPages = dataPages,
                    isPaginationDown = isPaginationDown,
                    paginationDirection = paginationDirection
                )
                return LoadResult.NothingToLoad()
            }
        } else {
            val dataSource = pagingSourcesManager.downPagingSources.elementAtOrNull(dataSourceIndex)
            if (dataSource == null) {
                val value = currentStatusFlow.value
                currentStatusFlow.value = PagingStatus.Success(
                    hasNextPage = false,
                    currentKey = if (value is PagingStatus.Success<Key>) value.currentKey
                    else null
                )
                currentStatusFlow.value = getSuccessStatus(
                    isLoadingPageInOrder = isLoadingPageInOrder,
                    pagingSourceWithIndex = lastPage?.pagingSourceWithIndex,
                    currentKey = lastPage?.currentPageKey,
                    dataPages = dataPages,
                    isPaginationDown = isPaginationDown,
                    paginationDirection = paginationDirection
                )
                return LoadResult.NothingToLoad()
            }
            nextPageKey = null
            pagingSourceWithIndex = dataSource to dataSourceIndex
        }
        if (isLoadingPageInOrder) {
            if (isPaginationDown) lastDownDataSourceIndex = pagingSourceWithIndex.second
            else lastUpDataSourceIndex = pagingSourceWithIndex.second
        }

        // picking currentKey and getting cache in case it was saved earlier
        val pagingSource = pagingSourceWithIndex.first
        val currentKey = nextPageKey ?: if (dataPages.isEmpty()) {
            pagingSource.defaultLoadParams?.key ?: loadParams.key
            ?: pageLoaderConfig.defaultParamsProvider().key
        } else null
        val pageAbsoluteIndex = if (lastPage == null) if (isPaginationDown) 0 else -1
        else if (isPaginationDown) lastPage.pageIndex + 1 else lastPage.pageIndex - 1

        val pageIndexInDataSource = if (lastPage?.dataSourceIndex == pagingSourceWithIndex.second) {
            lastPage.pageIndexInPagingSource + if (isPaginationDown) 1 else -1
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
        val lastStatus = currentStatusFlow.value
        val hasNextNow = nextPageKey != null

        val result = try {

            // setting status that we loading
            currentStatusFlow.value = PagingStatus.Loading(hasNextPage = hasNextNow)

            pagingSource.load(nextLoadParams)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                currentStatusFlow.value = lastStatus
                throw e
            }
            (pagingSource.pagingUnhandledErrorsHandler ?: pagingUnhandledErrorsHandler).handle(e)
        }

        // setting new status after loading completed
        val status = when (result) {
            is LoadResult.Success -> getSuccessStatus(
                isLoadingPageInOrder = isLoadingPageInOrder,
                pagingSourceWithIndex = pagingSourceWithIndex,
                currentKey = currentKey,
                nextPageKey = result.nextPageKey,
                dataPages = dataPages,
                isPaginationDown = isPaginationDown,
                paginationDirection = paginationDirection
            )

            is LoadResult.Failure -> PagingStatus.Failure(
                throwable = result.throwable,
                hasNextPage = hasNextNow
            )

            is LoadResult.NothingToLoad -> {
                dataPagesManager.notifyIfNeeded(result.returnData)
                getSuccessStatus(
                    isLoadingPageInOrder = isLoadingPageInOrder,
                    pagingSourceWithIndex = pagingSourceWithIndex,
                    currentKey = currentKey,
                    dataPages = dataPages,
                    isPaginationDown = isPaginationDown,
                    paginationDirection = paginationDirection
                )
            }
        }

        if (result !is LoadResult.Success) {
            currentStatusFlow.tryEmit(status)

            return result
        }

        val shouldReturnAwaitJob = loadParams.pagingParams
            ?.getOrNull(PagingLibraryParamsKeys.ReturnAwaitJob) == true

        val dataSetChannel = if (shouldReturnAwaitJob) Channel<Unit>(1) else null
        val job: Job? = if (shouldReturnAwaitJob) {
            pageLoaderConfig.coroutineScope.launch {
                withTimeoutOrNull(15000) {
                    dataSetChannel?.receive()
                }
            }
        } else null
        withContext(NonCancellable) {

            if (shouldSetNewStatus) currentStatusFlow.value = status

            // saving page to pages manager
            val previousPageKey =
                if (isPaginationDown) lastPage?.currentPageKey else result.nextPageKey
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
                pageIndexInPagingSource = pageIndexInDataSource,
                flow = if (result is LoadResult.Success.FlowSuccess) result.dataFlow else null,
                isPaginationDown = isPaginationDown,
                isNullified = false
            )

            val newIndex = currentLastPageIndex + if (isPaginationDown) 1 else -1

            dataPagesManager.setupPage(
                newIndex = newIndex,
                result = result,
                page = page,
                loadParams = loadParams,
                shouldReplaceOnConflict = shouldReplaceOnConflict,
                awaitDataSetChannel = dataSetChannel,
            )

            // updating indexes in case when inserted new page in middle of other pages
            if (!shouldReplaceOnConflict) dataPagesManager.updateIndexes()
        }

        val resultData = result.returnData?.toMutableParams()
            ?: if (job != null) MutablePagingParams() else null
        job?.let { resultData?.put(ReturnPagingLibraryKeys.DataSetJob, job) }

        val returnData = PagingParams(2 + if (shouldSetNewStatus) 0 else 1) {
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

        // preparing result
        return when (result) {
            is LoadResult.Success.SimpleSuccess -> {
                result.copy(
                    data = result.data,
                    nextPageKey = result.nextPageKey,
                    returnData = returnData
                )
            }

            is LoadResult.Success.FlowSuccess -> {
                result.copy(
                    dataFlow = result.dataFlow,
                    nextPageKey = result.nextPageKey,
                    returnData = returnData
                )
            }
        }
    }

    private inline fun getSuccessStatus(
        isLoadingPageInOrder: Boolean,
        pagingSourceWithIndex: Pair<PagingSource<Key, Data>, Int>?,
        currentKey: Key?,
        dataPages: ObjectList<DataPage<Key, Data>>,
        isPaginationDown: Boolean,
        paginationDirection: PaginationDirection,
        nextPageKey: Key? = null,
    ) = PagingStatus.Success(
        hasNextPage = (isLoadingPageInOrder && nextPageKey != null) ||
                pagingSourcesManager.findNextPagingSource(
                    currentPagingSource = if (isLoadingPageInOrder) pagingSourceWithIndex
                    else getLastSourceWithIndex(dataPages, isPaginationDown),
                    isThereKey = false,
                    paginationDirection = paginationDirection
                ) != null,
        currentKey = currentKey
    )

    private inline fun getLastSourceWithIndex(
        dataPages: ObjectList<DataPage<Key, Data>>,
        isPaginationDown: Boolean
    ) = (if (isPaginationDown) dataPages.lastOrNull()
    else dataPages.firstOrNull { !it.isNullified })?.pagingSourceWithIndex

    fun changeHasNextStatus(isPaginationDown: Boolean, hasNext: Boolean) {
        val stateFlow = if (isPaginationDown) downPagingStatus else upPagingStatus
        stateFlow.value = stateFlow.value.mapHasNext(hasNext)
    }
}
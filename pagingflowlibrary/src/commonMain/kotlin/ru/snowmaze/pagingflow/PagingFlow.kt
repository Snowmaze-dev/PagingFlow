package ru.snowmaze.pagingflow

import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.params.LoadSeveralPagesData
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.mapDataPresenter
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.source.ConcatPagingSource
import ru.snowmaze.pagingflow.source.PageLoaderConfig
import ru.snowmaze.pagingflow.source.PagingSource
import ru.snowmaze.pagingflow.utils.DiffOperation
import ru.snowmaze.pagingflow.utils.fastForEach

/**
 * Main class of library which holds state of pagination
 * You can create it using [buildPagingFlow]
 * To get current list of pagination you need to create presenter which will present list
 * You can use extension [pagingDataPresenter] on paging flow to get simply presenter
 * You can also create mapping presenter with [mapDataPresenter]
 */
class PagingFlow<Key : Any, Data : Any>(
    private val concatDataSource: ConcatPagingSource<Key, Data>,
    val pagingFlowConfiguration: PagingFlowConfiguration<Key>
) : PagingDataChangesMedium<Key, Data> {

    val upPagingStatus = concatDataSource.upPagingStatus
    val downPagingStatus = concatDataSource.downPagingStatus
    val isLoading get() = concatDataSource.isLoading

    val currentPagesCount get() = concatDataSource.currentPagesCount

    override val config = concatDataSource.config

    val firstPageInfo get() = concatDataSource.firstPageInfo
    val lastPageInfo get() = concatDataSource.lastPageInfo
    val pagesInfo get() = concatDataSource.pagesInfo

    val pagesCount: Int get() = concatDataSource.pagesCount

    /**
     * @see [ConcatPagingSource.addPagingSource]
     */
    fun addPagingSource(pagingSource: PagingSource<Key, Data>) {
        concatDataSource.addPagingSource(pagingSource)
    }

    /**
     * @see [ConcatPagingSource.removePagingSource]
     */
    fun removePagingSource(pagingSource: PagingSource<Key, Data>) {
        concatDataSource.removePagingSource(pagingSource)
    }

    fun removePagingSource(dataSourceIndex: Int) {
        concatDataSource.removePagingSource(dataSourceIndex)
    }

    suspend fun invalidateAndSetPagingSources(pagingSourceList: List<PagingSource<Key, Data>>) {
        concatDataSource.invalidateAndSetPagingSources(pagingSourceList)
    }

    suspend fun setPagingSources(
        pagingSourceList: List<PagingSource<Key, Data>>, diff: (
            oldList: List<PagingSource<Key, Data>>, newList: List<PagingSource<Key, Data>>
        ) -> List<DiffOperation<PagingSource<Key, Data>>>
    ) = concatDataSource.setPagingSources(
        newPagingSourceList = pagingSourceList, diff = diff
    )

    /**
     * @see [ConcatPagingSource.addDataChangedCallback]
     */
    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        concatDataSource.addDataChangedCallback(callback)
    }

    /**
     * @see [ConcatPagingSource.removeDataChangedCallback]
     */
    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>): Boolean {
        return concatDataSource.removeDataChangedCallback(callback)
    }

    /**
     * Loads next page async
     * @see loadNextPageInternal
     */
    fun loadNextPage(
        paginationDirection: PaginationDirection = pagingFlowConfiguration.defaultParamsProvider().paginationDirection,
        pagingParams: PagingParams? = null
    ) = pagingFlowConfiguration.coroutineScope.launch {
        loadNextPageWithResult(paginationDirection, pagingParams)
    }

    /**
     * Loads next page sync
     * @see loadNextPageInternal
     */
    suspend fun loadNextPageWithResult(
        paginationDirection: PaginationDirection = pagingFlowConfiguration.defaultParamsProvider().paginationDirection,
        pagingParams: PagingParams? = null
    ) = pagingFlowConfiguration.processingDispatcher {
        loadNextPageInternal(paginationDirection, pagingParams)
    }

    /**
     * Loads next page from [ConcatPagingSource]
     * @param paginationDirection direction of loading
     * @param pagingParams params which will be supplied to paging source, you can use them to specify custom values
     * @return [LoadNextPageResult] of loading next page of data in given direction
     */
    private suspend fun loadNextPageInternal(
        paginationDirection: PaginationDirection, pagingParams: PagingParams? = null
    ): LoadNextPageResult<Key, Data> {
        val defaultParams = pagingFlowConfiguration.defaultParamsProvider()
        val defaultPagingParams = defaultParams.pagingParams

        val loadData = concatDataSource.load(
            defaultParams.copy(
                paginationDirection = paginationDirection,
                pagingParams = defaultPagingParams?.let {
                    PagingParams(it)
                }?.apply {
                    pagingParams?.let { put(it) }
                } ?: pagingParams
            )
        )
        val result = loadData.returnData?.getOrNull(concatDataSource.concatSourceResultKey)
        val returnData = result?.returnData ?: loadData.returnData ?: PagingParams.EMPTY
        return when (loadData) {
            is LoadResult.Success<Key, Data> -> LoadNextPageResult.Success(
                currentKey = result?.currentKey,
                dataFlow = loadData.dataFlow,
                hasNext = result?.hasNext ?: false,
                returnData = returnData,
                nextPageKey = loadData.nextPageKey
            )

            is LoadResult.Failure<*, *> -> LoadNextPageResult.Failure(
                returnData = returnData, throwable = loadData.throwable
            )

            is LoadResult.NothingToLoad<Key, Data> -> LoadNextPageResult.NothingToLoad(
                returnData = returnData,
            )
        }
    }

    /**
     * @see [ConcatPagingSource.invalidate]
     */
    suspend fun invalidate(
        invalidateBehavior: InvalidateBehavior? = null, removeCachedData: Boolean = true
    ) = concatDataSource.invalidate(invalidateBehavior, removeCachedData = removeCachedData)
}

/**
 * Creates paging flow
 *
 * Usage:
 * ```
 * buildPagingFlow(
 *   configuration = PagingFlowConfiguration(defaultParams = LoadParams(pageSize = 100, key = 0)),
 *   loadFirstPage = false,
 * ) {
 *     addDataSource(FirstDataSource())
 *     addDataSource(SecondDataSource())
 *     addDataSource(ThirdDataSource())
 * }
 * ```
 *
 * @param configuration configuration of PagingFlow
 * @param loadFirstPage should load first page just after creating PagingFlow
 * @param builder configures PagingFlow
 */
fun <Key : Any, Data : Any> buildPagingFlow(
    configuration: PagingFlowConfiguration<Key>,
    loadFirstPage: Boolean = false,
    builder: PagingFlow<Key, Data>.() -> Unit = {}
) = PagingFlow<Key, Data>(
    ConcatPagingSource(
        PageLoaderConfig(
            defaultParamsProvider = configuration.defaultParamsProvider,
            maxItemsConfiguration = configuration.maxItemsConfiguration,
            processingDispatcher = configuration.processingDispatcher,
            coroutineScope = configuration.coroutineScope,
        )
    ), configuration
).apply {
    apply(builder)
    if (loadFirstPage) loadNextPage()
}

/**
 * Creates paging flow
 *
 * Usage:
 * ```
 * buildPagingFlow(
 *   configuration = PagingFlowConfiguration(defaultParams = LoadParams(pageSize = 100, key = 0)),
 *   loadFirstPage = false,
 *   FirstDataSource(),
 *   SecondDataSource(),
 *   ThirdDataSource(),
 *   ...
 * )
 * ```
 *
 * @param configuration configuration of PagingFlow
 * @param loadFirstPage should load first page just after creating PagingFlow
 * @param pagingSources paging sources list to be added to PagingFlow
 */
fun <Key : Any, Data : Any> buildPagingFlow(
    configuration: PagingFlowConfiguration<Key>,
    loadFirstPage: Boolean,
    vararg pagingSources: PagingSource<Key, Data>
) = buildPagingFlow(configuration = configuration, loadFirstPage = loadFirstPage) {
    for (pagingSource in pagingSources) {
        addPagingSource(pagingSource)
    }
}

fun <Key : Any, Data : Any> buildPagingFlow(
    configuration: PagingFlowConfiguration<Key>,
    vararg pagingSources: PagingSource<Key, Data>
) = buildPagingFlow(configuration = configuration) {
    for (pagingSource in pagingSources) {
        addPagingSource(pagingSource)
    }
}

/**
 * Loads multiple pages at once and only then delivers events to change listeners
 *
 * @param paginationDirection the direction in which to load pages.
 * @param awaitDataSet if true, waits for the data to be set to the presenter
 * @param awaitTimeout the maximum time, in milliseconds, to wait for [awaitDataSet] completion.
 * @param pagingParams params of loading
 * @param onSuccess callback invoked with the result of a successful page load.
 * @param getPagingParams a lambda that decides if additional pages should be loaded. If it returns non-null [PagingParams],
 * the next page is loaded with these params; otherwise, loading stops.
 *
 * @return result of loading
 */
suspend fun <Key : Any, Data : Any> PagingFlow<Key, Data>.loadSeveralPages(
    paginationDirection: PaginationDirection = pagingFlowConfiguration.defaultParamsProvider()
        .paginationDirection,
    awaitDataSet: Boolean = false,
    awaitTimeout: Long? = null,
    pagingParams: PagingParams? = null,
    onSuccess: ((LoadResult.Success<Key, Data>) -> Unit)? = null,
    getPagingParams: (LoadResult<Key, Data>?) -> PagingParams?,
): LoadNextPageResult<Key, Data> {
    val result = loadNextPageWithResult(
        paginationDirection = paginationDirection,
        pagingParams = (pagingParams ?: PagingParams(1)).apply {
            put(
                PagingLibraryParamsKeys.LoadSeveralPages, LoadSeveralPagesData(
                    getPagingParams = {
                        val pageLoadParams = getPagingParams(it) ?: return@LoadSeveralPagesData null
                        if (awaitDataSet) {
                            pageLoadParams.put(PagingLibraryParamsKeys.ReturnAwaitJob, true)
                        }
                        pageLoadParams
                    },
                    onSuccess = onSuccess
                ) as LoadSeveralPagesData<Any, Any>
            )
        }
    )
    if (awaitDataSet) {
        val awaitData = suspend {
            result.returnData.getOrNull(ReturnPagingLibraryKeys.PagingParamsList)?.fastForEach {
                it?.getOrNull(ReturnPagingLibraryKeys.DataSetJob)?.join()
            }
        }
        if (awaitTimeout == null) awaitData()
        else withTimeoutOrNull(awaitTimeout) { awaitData() }
    }
    return result
}

/**
 * Loads page and then suspends until data is set to presenter.
 *
 * If [timeout] is passed then it will stop waiting if this timeout was exceeded.
 *
 * @param paginationDirection the direction in which to load pages.
 * @param timeout the maximum time, in milliseconds, to wait for data set to presenter.
 * @param pagingParams params of loading
 *
 * @return result of loading
 */
suspend fun <Key : Any, Data : Any> PagingFlow<Key, Data>.loadNextPageAndAwaitDataSet(
    paginationDirection: PaginationDirection = pagingFlowConfiguration.defaultParamsProvider()
        .paginationDirection,
    timeout: Long? = null,
    pagingParams: PagingParams? = null
): LoadNextPageResult<Key, Data> {
    val result = loadNextPageWithResult(
        paginationDirection = paginationDirection,
        pagingParams = (pagingParams ?: PagingParams(1)).apply {
            put(PagingLibraryParamsKeys.ReturnAwaitJob, true)
        }
    )
    val awaitData = suspend {
        result.returnData.getOrNull(ReturnPagingLibraryKeys.DataSetJob)?.join()
    }
    if (timeout == null) awaitData()
    else withTimeoutOrNull(timeout) { awaitData() }
    return result
}
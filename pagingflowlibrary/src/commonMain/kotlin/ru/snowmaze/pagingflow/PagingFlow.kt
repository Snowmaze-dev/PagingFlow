package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.errorshandler.DefaultPagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.errorshandler.PagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.params.MutablePagingParams
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.mapDataPresenter
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.source.ConcatPagingSource
import ru.snowmaze.pagingflow.source.PageLoaderConfig
import ru.snowmaze.pagingflow.source.PagingSource
import ru.snowmaze.pagingflow.utils.DiffOperation

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
     * @see [ConcatPagingSource.addDownPagingSource]
     */
    fun addDownPagingSource(pagingSource: PagingSource<Key, out Data>) {
        concatDataSource.addDownPagingSource(pagingSource)
    }

    fun addUpPagingSource(pagingSource: PagingSource<Key, out Data>) {
        concatDataSource.addUpPagingSource(pagingSource)
    }

    /**
     * @see [ConcatPagingSource.removePagingSource]
     */
    fun removePagingSource(pagingSource: PagingSource<Key, out Data>) {
        concatDataSource.removePagingSource(pagingSource)
    }

    fun removePagingSource(dataSourceIndex: Int) {
        concatDataSource.removePagingSource(dataSourceIndex)
    }

    suspend fun invalidateAndSetPagingSources(pagingSourceList: List<PagingSource<Key, out Data>>) {
        concatDataSource.invalidateAndSetPagingSources(pagingSourceList)
    }

    suspend fun setPagingSources(
        pagingSourceList: List<PagingSource<Key, out Data>>, diff: (
            oldList: List<PagingSource<Key, out Data>>, newList: List<PagingSource<Key, out Data>>
        ) -> List<DiffOperation<PagingSource<Key, out Data>>>
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
     * Loads pages using [ConcatPagingSource]
     * @param paginationDirection direction of loading
     * @param pagingParams params which will be supplied to paging source, you can use them to specify custom values
     * @return [LoadNextPageResult] of loading next pages of data in given direction
     */
    internal suspend fun load(
        paginationDirection: PaginationDirection?, pagingParams: MutablePagingParams? = null
    ): LoadNextPageResult<Key> {
        val defaultParams = pagingFlowConfiguration.defaultParamsProvider()
        val defaultPagingParams = defaultParams.pagingParams
        val pickedPaginationDirection = paginationDirection
            ?: pagingFlowConfiguration.defaultParamsProvider().paginationDirection

        val loadData = concatDataSource.load(
            loadParams = defaultParams.copy(
                paginationDirection = pickedPaginationDirection,
                pagingParams = defaultPagingParams?.let {
                    MutablePagingParams(it)
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
    pagingUnhandledErrorsHandler: PagingUnhandledErrorsHandler<Key, Data> = DefaultPagingUnhandledErrorsHandler(),
    loadFirstPage: Boolean = false,
    builder: PagingFlow<Key, Data>.() -> Unit = {}
) = PagingFlow<Key, Data>(
    ConcatPagingSource(
        PageLoaderConfig(
            defaultParamsProvider = configuration.defaultParamsProvider,
            maxItemsConfiguration = configuration.maxItemsConfiguration,
            processingDispatcher = configuration.processingDispatcher,
            coroutineScope = configuration.coroutineScope,
            shouldStorePageItems = configuration.shouldStorePageItems,
            shouldCollectOnlyLatest = configuration.shouldCollectOnlyLatest
        ),
        pagingUnhandledErrorsHandler = pagingUnhandledErrorsHandler
    ),
    configuration,
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
    vararg pagingSources: PagingSource<Key, out Data>,
    pagingUnhandledErrorsHandler: PagingUnhandledErrorsHandler<Key, Data> = DefaultPagingUnhandledErrorsHandler(),
) = buildPagingFlow(
    configuration = configuration,
    loadFirstPage = loadFirstPage,
    pagingUnhandledErrorsHandler = pagingUnhandledErrorsHandler
) {
    for (pagingSource in pagingSources) {
        addDownPagingSource(pagingSource)
    }
}

fun <Key : Any, Data : Any> buildPagingFlow(
    configuration: PagingFlowConfiguration<Key>,
    vararg pagingSources: PagingSource<Key, out Data>,
    pagingUnhandledErrorsHandler: PagingUnhandledErrorsHandler<Key, Data> = DefaultPagingUnhandledErrorsHandler(),
) = buildPagingFlow(
    configuration = configuration,
    pagingUnhandledErrorsHandler = pagingUnhandledErrorsHandler
) {
    for (pagingSource in pagingSources) {
        addDownPagingSource(pagingSource)
    }
}
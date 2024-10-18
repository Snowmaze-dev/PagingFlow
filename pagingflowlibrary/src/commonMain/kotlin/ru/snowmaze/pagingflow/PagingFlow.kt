package ru.snowmaze.pagingflow

import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.mapDataPresenter
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.sources.ConcatDataSource
import ru.snowmaze.pagingflow.sources.ConcatDataSourceConfig
import ru.snowmaze.pagingflow.sources.DataSource
import ru.snowmaze.pagingflow.utils.DiffOperation

/**
 * Main class of library which holds state of pagination
 * You can create it using [buildPagingFlow]
 * To get current list of pagination you need to create presenter which will present list
 * You can use extension [pagingDataPresenter] on paging flow to get simply presenter
 * You can also create mapping presenter with [mapDataPresenter]
 */
class PagingFlow<Key : Any, Data : Any>(
    private val concatDataSource: ConcatDataSource<Key, Data>,
    val pagingFlowConfiguration: PagingFlowConfiguration<Key>
) : PagingDataChangesMedium<Key, Data> {

    private val loadMutex = Mutex()
    val upPagingStatus = concatDataSource.upPagingStatus
    val downPagingStatus = concatDataSource.downPagingStatus
    val isLoading get() = loadMutex.isLocked

    val currentPagesCount get() = concatDataSource.currentPagesCount

    override val config = concatDataSource.config

    val firstPageInfo get() = concatDataSource.firstPageInfo
    val lastPageInfo get() = concatDataSource.lastPageInfo
    val pagesInfo get() = concatDataSource.pagesInfo

    val pagesCount: Int get() = concatDataSource.pagesCount

    /**
     * @see [ConcatDataSource.addDataSource]
     */
    fun addDataSource(dataSource: DataSource<Key, Data>) {
        concatDataSource.addDataSource(dataSource)
    }

    /**
     * @see [ConcatDataSource.removeDataSource]
     */
    fun removeDataSource(dataSource: DataSource<Key, Data>) {
        concatDataSource.removeDataSource(dataSource)
    }

    fun removeDataSource(dataSourceIndex: Int) {
        concatDataSource.removeDataSource(dataSourceIndex)
    }

    suspend fun invalidateAndSetDataSources(dataSourceList: List<DataSource<Key, Data>>) {
        concatDataSource.invalidateAndSetDataSources(dataSourceList)
    }

    suspend fun setDataSources(
        dataSourceList: List<DataSource<Key, Data>>, diff: (
            oldList: List<DataSource<Key, Data>>, newList: List<DataSource<Key, Data>>
        ) -> List<DiffOperation<DataSource<Key, Data>>>
    ) = concatDataSource.setDataSources(
        newDataSourceList = dataSourceList, diff = diff
    )

    /**
     * @see [ConcatDataSource.addDataChangedCallback]
     */
    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        concatDataSource.addDataChangedCallback(callback)
    }

    /**
     * @see [ConcatDataSource.removeDataChangedCallback]
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
    ) = loadMutex.withLock {
        pagingFlowConfiguration.processingDispatcher {
            loadNextPageInternal(paginationDirection, pagingParams)
        }
    }

    /**
     * Loads next page from [ConcatDataSource]
     * @param paginationDirection direction of loading
     * @param pagingParams params which will be supplied to data source, you can use them to specify custom values
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
                pagingParams = defaultPagingParams?.apply {
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
     * @see [ConcatDataSource.invalidate]
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
    ConcatDataSource(
        ConcatDataSourceConfig(
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
 * @param dataSources data sources list to be added to PagingFlow
 */
fun <Key : Any, Data : Any> buildPagingFlow(
    configuration: PagingFlowConfiguration<Key>,
    loadFirstPage: Boolean = false,
    vararg dataSources: DataSource<Key, Data>
) = buildPagingFlow(configuration = configuration, loadFirstPage = loadFirstPage) {
    for (dataSource in dataSources) {
        addDataSource(dataSource)
    }
}
fun <Key : Any, Data : Any> buildPagingFlow(
    configuration: PagingFlowConfiguration<Key>,
    vararg dataSources: DataSource<Key, Data>
) = buildPagingFlow(configuration = configuration) {
    for (dataSource in dataSources) {
        addDataSource(dataSource)
    }
}
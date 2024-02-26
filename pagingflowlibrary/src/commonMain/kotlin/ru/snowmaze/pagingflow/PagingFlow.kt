package ru.snowmaze.pagingflow

import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.sources.ConcatDataSource
import ru.snowmaze.pagingflow.sources.ConcatDataSourceConfig
import ru.snowmaze.pagingflow.sources.DataSource
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.presenters.mappingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.result.LoadResult

/**
 * Main class of library which holds state of pagination
 * You can create it using [buildPagingFlow]
 * To get current list of pagination you need to create presenter which will present list
 * You can use extension [pagingDataPresenter] on paging flow to get simply presenter
 * You can also create mapping presenter with [mappingDataPresenter]
 */
class PagingFlow<Key : Any, Data : Any, PagingStatus : Any>(
    private val concatDataSource: ConcatDataSource<Key, Data, PagingStatus>,
    val pagingFlowConfiguration: PagingFlowConfiguration<Key>
) : PagingDataChangesMedium<Key, Data> {

    private val loadMutex = Mutex()
    val upPagingStatus get() = concatDataSource.upPagingStatus
    val downPagingStatus get() = concatDataSource.downPagingStatus
    val isLoading get() = loadMutex.isLocked

    val currentPagesCount get() = concatDataSource.currentPagesCount

    override val config = concatDataSource.config

    /**
     * @see [ConcatDataSource.addDataSource]
     */
    fun addDataSource(dataSource: DataSource<Key, Data, PagingStatus>) {
        concatDataSource.addDataSource(dataSource)
    }

    /**
     * @see [ConcatDataSource.removeDataSource]
     */
    fun removeDataSource(dataSource: DataSource<Key, Data, PagingStatus>) {
        concatDataSource.removeDataSource(dataSource)
    }

    /**
     * @see [ConcatDataSource.addDataChangedCallback]
     */
    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        concatDataSource.addDataChangedCallback(callback)
    }

    /**
     * @see [ConcatDataSource.removeDataChangedCallback]
     */
    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        concatDataSource.removeDataChangedCallback(callback)
    }


    /**
     * Loads next page async
     * @see loadNextPageInternal
     */
    fun loadNextPage(
        paginationDirection: PaginationDirection = pagingFlowConfiguration.defaultParamsProvider()
            .paginationDirection,
        pagingParams: PagingParams? = null
    ) = pagingFlowConfiguration.coroutineScope.launch {
        loadNextPageWithResult(paginationDirection, pagingParams)
    }

    /**
     * Loads next page sync
     * @see loadNextPageInternal
     */
    suspend fun loadNextPageWithResult(
        paginationDirection: PaginationDirection = pagingFlowConfiguration.defaultParamsProvider()
            .paginationDirection,
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
        paginationDirection: PaginationDirection,
        pagingParams: PagingParams? = null
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
        val result = loadData.additionalData?.getOrNull(
            ConcatDataSource.concatDataSourceResultKey<Key>()
        )
        val additionalData = result?.additionalData ?: loadData.additionalData
        return when (loadData) {
            is LoadResult.Success<Key, Data, PagingStatus> -> LoadNextPageResult.Success(
                currentKey = result?.currentKey,
                dataFlow = loadData.dataFlow,
                hasNext = result?.hasNext ?: false,
                additionalData = additionalData ?: PagingParams()
            )

            is LoadResult.Failure<*, *, PagingStatus> -> LoadNextPageResult.Failure(
                additionalData = additionalData ?: PagingParams(),
                throwable = loadData.throwable
            )
        }
    }

    /**
     * @see [ConcatDataSource.invalidate]
     */
    suspend fun invalidate(removeCachedData: Boolean = true) =
        concatDataSource.invalidate(removeCachedData = removeCachedData)
}

fun <Key : Any, Data : Any, PagingStatus : Any> buildPagingFlow(
    configuration: PagingFlowConfiguration<Key>,
    builder: PagingFlow<Key, Data, PagingStatus>.() -> Unit
) = PagingFlow<Key, Data, PagingStatus>(
    ConcatDataSource(
        ConcatDataSourceConfig(
            defaultParamsProvider = configuration.defaultParamsProvider,
            maxItemsCount = configuration.maxItemsCount,
            maxCachedResultPagesCount = configuration.maxCachedResultPagesCount,
            processingDispatcher = configuration.processingDispatcher,
            coroutineScope = configuration.coroutineScope,
            shouldFillRemovedPagesWithNulls = configuration.enableDroppedPagesNullPlaceholders
        )
    ), configuration
).apply(builder)
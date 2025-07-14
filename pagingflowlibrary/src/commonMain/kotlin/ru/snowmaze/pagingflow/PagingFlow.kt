package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.params.MutablePagingParams
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.mapDataPresenter
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.source.ConcatPagingSource
import ru.snowmaze.pagingflow.source.PagingSource
import ru.snowmaze.pagingflow.utils.DiffOperation

/**
 * Main class of library which holds state of pagination
 * You can create it using [buildPagingFlow]
 * To get current list of pagination you need to create presenter which will present list
 * You can use extension [pagingDataPresenter] on paging flow to get simply presenter
 * You can also create mapping presenter with [mapDataPresenter]
 */
open class PagingFlow<Key : Any, Data : Any>(
    private val concatDataSource: ConcatPagingSource<Key, Data>,
    override val pagingFlowConfiguration: PagingFlowConfiguration<Key>
) : PagingDataChangesMedium<Key, Data>, PagingFlowLoader<Key> {

    override val upPagingStatus = concatDataSource.upPagingStatus
    override val downPagingStatus = concatDataSource.downPagingStatus

    override val pagesCount get() = concatDataSource.pagesCount

    override val config = concatDataSource.config

    override val notNullifiedPagesCount: Int get() = concatDataSource.notNullifiedPagesCount

    val firstPageInfo get() = concatDataSource.firstPageInfo
    val lastPageInfo get() = concatDataSource.lastPageInfo
    val pagesInfo get() = concatDataSource.pagesInfo

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

    override suspend fun invalidate(
        invalidateBehavior: InvalidateBehavior?,
        removeCachedData: Boolean,
    ) = concatDataSource.invalidate(
        removeCachedData = removeCachedData,
        invalidateBehavior = invalidateBehavior
    )
}
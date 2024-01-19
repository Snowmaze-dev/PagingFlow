package ru.snowmaze.pagingflow.sources

import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.LoadResult
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.mapSuccess

abstract class SegmentedIntDataSource<Data : Any, PagingStatus : Any> :
    DataSource<Int, Data, PagingStatus> {

    abstract val totalCount: Int

    override suspend fun loadData(loadParams: LoadParams<Int>): LoadResult<Int, Data, PagingStatus> {
        val startIndex = loadParams.key ?: 0
        val loadSize = (totalCount - startIndex).coerceAtMost(loadParams.loadSize)
        val isDown = loadParams.paginationDirection == PaginationDirection.DOWN
        val endIndex = startIndex + loadSize
        val howManyLeft = if (isDown) totalCount - endIndex else startIndex
        val nextPageKey = if (howManyLeft == 0) null
        else if (isDown) endIndex else startIndex - loadSize
        return loadData(loadParams, startIndex, endIndex).mapSuccess { result ->
            if (result.nextNextPageKey == null) result.copy(nextNextPageKey = nextPageKey)
            else result
        }
    }

    protected abstract suspend fun loadData(
        loadParams: LoadParams<Int>,
        startIndex: Int,
        endIndex: Int
    ): LoadResult<Int, Data, PagingStatus>
}
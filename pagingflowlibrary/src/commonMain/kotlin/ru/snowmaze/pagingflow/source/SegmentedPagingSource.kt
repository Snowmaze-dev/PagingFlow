package ru.snowmaze.pagingflow.source

import kotlinx.coroutines.flow.map
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.result.mapSuccess

abstract class SegmentedPagingSource<Data : Any> : PagingSource<Int, Data> {

    abstract val totalCount: Int

    override suspend fun load(loadParams: LoadParams<Int>): LoadResult<Int, Data> {
        val startIndex = loadParams.key ?: 0
        val pageSize = (totalCount - startIndex).coerceAtMost(loadParams.pageSize)
        val isDown = loadParams.paginationDirection == PaginationDirection.DOWN
        val endIndex = startIndex + pageSize
        val howManyLeft = if (isDown) totalCount - endIndex else startIndex
        val nextPageKey = if (howManyLeft == 0) null
        else if (isDown) endIndex else startIndex - pageSize
        return loadData(loadParams, startIndex, endIndex).mapSuccess { result ->
            if (result.nextPageKey == null) result.copy(dataFlow = result.dataFlow?.map {
                UpdatableData(it.data, it.nextPageKey ?: nextPageKey)
            }, nextPageKey = nextPageKey)
            else result
        }
    }

    protected abstract suspend fun loadData(
        loadParams: LoadParams<Int>,
        startIndex: Int,
        endIndex: Int
    ): LoadResult<Int, Data>
}
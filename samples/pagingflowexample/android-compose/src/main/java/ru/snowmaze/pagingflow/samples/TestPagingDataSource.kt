package ru.snowmaze.pagingflow.samples

import androidx.paging.PagingSource
import androidx.paging.PagingState
import ru.snowmaze.pagingflow.LoadParams

class TestPagingDataSource(itemsCount: Int) : PagingSource<Int, String>() {

    private val testDataSource = TestPagingSource(itemsCount)

    override fun getRefreshKey(
        state: PagingState<Int, String>
    ): Int? = state.anchorPosition?.let { anchorPosition ->
        state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
            ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> {
        val position = params.key ?: 0
        val result = testDataSource.load(
            LoadParams(
                params.loadSize, position
            )
        ) as ru.snowmaze.pagingflow.result.LoadResult.Success.SimpleSuccess
        return LoadResult.Page(
            result.data as List<String>,
            prevKey = params.key?.minus(params.loadSize)?.takeIf { it >= 0 },
            nextKey = result.nextPageKey
        )
    }
}
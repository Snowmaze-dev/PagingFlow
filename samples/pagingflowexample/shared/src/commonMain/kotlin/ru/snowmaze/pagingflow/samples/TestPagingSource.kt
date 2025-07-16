package ru.snowmaze.pagingflow.samples

import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.result.result
import ru.snowmaze.pagingflow.source.SegmentedPagingSource

class TestPagingSource(
    override val totalCount: Int,
) : SegmentedPagingSource<String>() {

    private val items = buildList {
        repeat(totalCount) {
            add("Item $it")
        }
    }

    override suspend fun loadData(
        loadParams: LoadParams<Int>,
        startIndex: Int,
        endIndex: Int
    ): LoadResult.Success<Int, String> {
        return result(items.subList(startIndex, endIndex))
    }
}
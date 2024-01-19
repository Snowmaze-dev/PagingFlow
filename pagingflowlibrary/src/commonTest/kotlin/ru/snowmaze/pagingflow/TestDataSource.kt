package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.result.simpleResult
import ru.snowmaze.pagingflow.sources.SegmentedDataSource

class TestDataSource(
    override val totalCount: Int
) : SegmentedDataSource<String, DefaultPagingStatus>() {

    var currentStatus: DefaultPagingStatus? = null
    var currentException: Exception? = null

    private val items = buildList {
        repeat(totalCount) {
            add("Item $it")
        }
    }

    fun getItems(count: Int) = items.subList(0, count)

    override suspend fun loadData(
        loadParams: LoadParams<Int>,
        startIndex: Int,
        endIndex: Int
    ): LoadResult<Int, String, DefaultPagingStatus> {
        val exception = currentException
        if (exception != null) throw exception
        return simpleResult(items.subList(startIndex, endIndex), status = currentStatus)
    }
}
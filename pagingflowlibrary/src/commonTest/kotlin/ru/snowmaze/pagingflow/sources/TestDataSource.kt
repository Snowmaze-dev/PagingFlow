package ru.snowmaze.pagingflow.sources

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.result.result

class TestDataSource(
    override val totalCount: Int,
    private val delayProvider: () -> Long = { 0L },
    startFrom: Int = 0
) : SegmentedDataSource<String>() {

    var currentException: Exception? = null

    private val items = buildList {
        for (item in startFrom until totalCount) {
            add("Item $item")
        }
    }

    fun getItems(count: Int) = items.subList(0, count)

    override suspend fun loadData(
        loadParams: LoadParams<Int>,
        startIndex: Int,
        endIndex: Int
    ): LoadResult<Int, String> {
        val exception = currentException
        if (exception != null) throw exception
        return result(dataFlow = flow {
            delay(delayProvider())
            emit(items.subList(startIndex, endIndex))
        })
    }
}
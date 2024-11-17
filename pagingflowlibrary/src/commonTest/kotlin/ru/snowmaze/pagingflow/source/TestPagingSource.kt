package ru.snowmaze.pagingflow.source

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.result.result

class TestPagingSource(
    override val totalCount: Int,
    private val delayProvider: () -> Long = { 0L },
    private val isReversed: Boolean = false,
    startFrom: Int = 0
) : SegmentedPagingSource<String>() {

    var currentException: Exception? = null

    private val items = buildList {
        for (item in startFrom until totalCount) {
            add("Item ${if (isReversed) totalCount - item else item}")
        }
    }

    fun getItems(count: Int, startFrom: Int = 0): List<String> {
        val items = items.subList(startFrom, startFrom + count)
        return if (isReversed) items.asReversed() else items
    }

    override suspend fun loadData(
        loadParams: LoadParams<Int>,
        startIndex: Int,
        endIndex: Int
    ): LoadResult<Int, String> {
        val exception = currentException
        if (exception != null) throw exception
        return result(dataFlow = flow {
            delay(delayProvider())
            val list = items.subList(startIndex, endIndex)
            emit(if (isReversed) list.asReversed() else list)
        })
    }
}
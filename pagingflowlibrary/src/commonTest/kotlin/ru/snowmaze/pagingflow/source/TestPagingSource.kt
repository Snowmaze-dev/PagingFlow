package ru.snowmaze.pagingflow.source

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.result.result

class TestPagingSource(
    override val totalCount: Int,
    private val flowDelayProvider: () -> Long = { 0L },
    private val loadDelay: () -> Long = { 0L},
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
        delay(loadDelay())
        val delay = flowDelayProvider()
        val list = items.subList(startIndex, endIndex)
        val finalList = if (isReversed) list.asReversed() else list
        return if (delay == 0L) result(finalList) else result(dataFlow = flow {
            delay(delay)
            emit(finalList)
        })
    }
}
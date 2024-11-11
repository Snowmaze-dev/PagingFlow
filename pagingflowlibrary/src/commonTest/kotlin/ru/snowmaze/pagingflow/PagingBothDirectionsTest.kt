package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.test.Test

class PagingBothDirectionsTest {

    val totalCount = 1000
    val pageSize = 20

    @Test
    fun testPagingBothDirections() = runTestOnDispatchersDefault {
        val pagingFlow = buildPagingFlow(
            PagingFlowConfiguration(
                defaultParams = LoadParams(pageSize),
                processingDispatcher = Dispatchers.Default
            ),
            TestPagingSource(totalCount)
        )
        val upPagingSource = TestPagingSource(totalCount, isReversed = true)
        pagingFlow.addUpPagingSource(upPagingSource)
        val presenter = pagingFlow.pagingDataPresenter()
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        presenter.dataFlow.firstEqualsWithTimeout(upPagingSource.getItems(pageSize))
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        presenter.dataFlow.firstEqualsWithTimeout(upPagingSource.getItems(pageSize * 2))
    }
}
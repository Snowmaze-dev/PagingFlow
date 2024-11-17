package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import ru.snowmaze.pagingflow.presenters.SimplePresenterConfiguration
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.list.DiffListBuildStrategy
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.source.MaxItemsConfiguration
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.test.Test

class PagingBothDirectionsTest {

    val totalCount = 1000
    val pageSize = 20

    @Test
    fun testPagingBothDirections() = runTestOnDispatchersDefault {
        val downPagingSource = TestPagingSource(totalCount)
        val pagingFlow = buildPagingFlow(
            PagingFlowConfiguration(
                defaultParams = LoadParams(pageSize),
                processingDispatcher = Dispatchers.Default,
                maxItemsConfiguration = MaxItemsConfiguration(
                    maxItemsCount = 80,
                    enableDroppedPagesNullPlaceholders = true
                )
            ),
            downPagingSource
        )
        val upPagingSource = TestPagingSource(totalCount, isReversed = true)
        pagingFlow.addUpPagingSource(upPagingSource)
        val presenter = pagingFlow.pagingDataPresenter(
            configuration = SimplePresenterConfiguration(
                listBuildStrategy = DiffListBuildStrategy()
            )
        )
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        presenter.dataFlow.firstEqualsWithTimeout(upPagingSource.getItems(pageSize))
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        val upItems = upPagingSource.getItems(count = pageSize, startFrom = pageSize) +
                upPagingSource.getItems(pageSize)
        presenter.dataFlow.firstEqualsWithTimeout(upItems)
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.DOWN)
        presenter.dataFlow.firstEqualsWithTimeout(upItems + downPagingSource.getItems(pageSize))
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        presenter.dataFlow.firstEqualsWithTimeout(
            upPagingSource.getItems(
                count = pageSize,
                startFrom = pageSize * 2
            ) + upItems + downPagingSource.getItems(pageSize)
        )
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)

        presenter.dataFlow.firstEqualsWithTimeout(
            upPagingSource.getItems(
                count = pageSize,
                startFrom = pageSize * 3
            ) + upPagingSource.getItems(
                count = pageSize,
                startFrom = pageSize * 2
            ) + upItems
        )
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)

        presenter.dataFlow.firstEqualsWithTimeout(
            upPagingSource.getItems(
                count = pageSize,
                startFrom = pageSize * 4
            ) + upPagingSource.getItems(
                count = pageSize,
                startFrom = pageSize * 3
            ) + upPagingSource.getItems(
                count = pageSize,
                startFrom = pageSize * 2
            ) + upPagingSource.getItems(
                count = pageSize,
                startFrom = pageSize
            ) + buildList<String?>(pageSize) {
                repeat(pageSize) {
                    add(null)
                }
            }
        )
    }
}
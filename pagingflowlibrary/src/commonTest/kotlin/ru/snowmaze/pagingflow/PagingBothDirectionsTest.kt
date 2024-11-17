package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import ru.snowmaze.pagingflow.presenters.SimplePresenterConfiguration
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.list.DiffListBuildStrategy
import ru.snowmaze.pagingflow.presenters.list.ListByPagesBuildStrategy
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.source.MaxItemsConfiguration
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.test.Test
import kotlin.test.assertEquals

class PagingBothDirectionsTest {

    val totalCount = 1000
    val pageSize = 20

    @Test
    fun testPagingBothDirections() = runTestOnDispatchersDefault {
        val downPagingSource = TestPagingSource(totalCount)
        val maxItems = pageSize * 4
        val pagingFlow = buildPagingFlow(
            PagingFlowConfiguration(
                defaultParams = LoadParams(pageSize),
                processingDispatcher = Dispatchers.Default,
                maxItemsConfiguration = MaxItemsConfiguration(
                    maxItemsCount = maxItems,
                    enableDroppedPagesNullPlaceholders = true
                )
            ),
            downPagingSource
        )
        val upPagingSource = TestPagingSource(totalCount, isReversed = true)
        pagingFlow.addUpPagingSource(upPagingSource)
        val presenter = pagingFlow.pagingDataPresenter(
            configuration = SimplePresenterConfiguration(
                listBuildStrategy = ListByPagesBuildStrategy()
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

        var pagesCount = 3

        while (true) {
            val result = pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
            if (result is LoadNextPageResult.NothingToLoad) {
                assertEquals(totalCount / pageSize, pagesCount)
                break
            }
            pagesCount++
            presenter.dataFlow.firstEqualsWithTimeout(
                buildUpPages(upPagingSource, pagesCount, pageSize, maxItems)
            )
        }
        while (true) {
            pagesCount--
            if (pagesCount == 3) break
            pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.DOWN)
            presenter.dataFlow.firstEqualsWithTimeout(
                buildUpPages(upPagingSource, pagesCount, pageSize, maxItems)
            )
        }
    }

    private fun buildUpPages(
        testPagingSource: TestPagingSource,
        pagesCount: Int,
        pageSize: Int,
        maxItemsCount: Int
    ): List<String?> = buildList(pagesCount * pageSize) {
        var itemsCount = 0
        for (index in 0 until pagesCount) {
            val pageIndex = pagesCount - index
            itemsCount += pageSize
            if (itemsCount > maxItemsCount) {
                addAll(buildList(pageSize) {
                    repeat(pageSize) {
                        add(null)
                    }
                })
                continue
            }
            addAll(
                testPagingSource.getItems(pageSize, startFrom = (pageIndex - 1) * pageSize)
            )
        }
    }
}
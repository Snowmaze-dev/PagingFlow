package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import ru.snowmaze.pagingflow.presenters.BasicPresenterConfiguration
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.list.ListByPagesBuildStrategy
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.presenters.statePresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.source.MaxItemsConfiguration
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PagingBothDirectionsTest {

    val totalCount = 1000
    val pageSize = 20

    @Test
    fun testPagingBothDirections() = runTestOnDispatchersDefault {
        val onNewLoadChannel = Channel<LoadParams<Int>>(
            1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val downPagingSource = TestPagingSource(totalCount, onNewLoadChannel = onNewLoadChannel)
        val maxPagesCount = 4
        val maxCachedResultPagesCount = maxPagesCount + 1
        val maxItems = pageSize * maxPagesCount
        val pagingFlow = buildPagingFlow(
            PagingFlowConfiguration(
                defaultParams = LoadParams(pageSize),
                processingDispatcher = Dispatchers.Default,
                maxItemsConfiguration = MaxItemsConfiguration(
                    maxItemsCount = maxItems,
                    maxCachedResultPagesCount = maxCachedResultPagesCount,
                    enableDroppedPagesNullPlaceholders = true
                )
            ),
            downPagingSource
        )
        val upPagingSource = TestPagingSource(
            totalCount,
            isReversed = true,
            onNewLoadChannel = onNewLoadChannel
        )
        pagingFlow.addUpPagingSource(upPagingSource)
        val presenter = pagingFlow.statePresenter(
            configuration = BasicPresenterConfiguration(
                listBuildStrategy = ListByPagesBuildStrategy()
            )
        )

        // one page up
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        presenter.dataFlow.firstEqualsWithTimeout(upPagingSource.getItems(pageSize))

        // two pages up
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        val upItems = upPagingSource.getItems(count = pageSize, startFrom = pageSize) +
                upPagingSource.getItems(pageSize)
        presenter.dataFlow.firstEqualsWithTimeout(upItems)

        // two pages up and one down
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.DOWN)
        presenter.dataFlow.firstEqualsWithTimeout(upItems + downPagingSource.getItems(pageSize))

        // three pages up and one down
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        presenter.dataFlow.firstEqualsWithTimeout(
            upPagingSource.getItems(
                count = pageSize,
                startFrom = pageSize * 2
            ) + upItems + downPagingSource.getItems(pageSize)
        )

        val startPagesCount = 3
        var pagesCount = startPagesCount

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
        var count = 0
        while (true) {
            pagesCount--
            if (pagesCount == startPagesCount) break
            pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.DOWN)
            presenter.dataFlow.firstEqualsWithTimeout(
                buildUpPages(upPagingSource, pagesCount, pageSize, maxItems)
            )
            count++
        }
        onNewLoadChannel.receive()
        pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        assertNotNull(onNewLoadChannel.receive().cachedResult)
        repeat(5) {
            pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
            assertNull(onNewLoadChannel.receive().cachedResult)
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
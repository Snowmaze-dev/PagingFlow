package ru.snowmaze.pagingflow

import kotlinx.coroutines.test.runTest
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import kotlin.test.Test
import kotlin.test.assertEquals

class PagingBothDirectionsTest {

    val pageSize = 20
    val removePagesOffset = 4
    val totalCount = 10000

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        maxPagesCount = removePagesOffset,
        processingDispatcher = testDispatcher,
        enableDroppedPagesNullPlaceholders = false
    )

    @Test
    fun loadBothDirectionsTest() = runTest {
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter( )
        pagingFlow.testLoadEverything(
            listOf(testDataSource),
            pageSize,
            pagingPresenter = presenter
        )

        assertEquals(removePagesOffset, pagingFlow.currentPagesCount)
        var loadedData = presenter.dataFlow.value
        val allItems = testDataSource.getItems(totalCount)
        assertEquals(
            allItems.takeLast(pageSize * removePagesOffset),
            loadedData
        )

        var hasNext = true
        var countOfPages = 0
        while (hasNext) {
            hasNext = pagingFlow.loadNextPageWithResult(PaginationDirection.UP).asSuccess().hasNext
            countOfPages++
            loadedData = presenter.dataFlow.value
            val (startIndex, endIndex) = getItemsWithPagesOffset(countOfPages)
            assertEquals(
                allItems.subList(startIndex, endIndex),
                loadedData
            )
        }
        loadedData = presenter.dataFlow.value
        assertEquals(testDataSource.getItems(pageSize * removePagesOffset), loadedData)
        assertEquals(
            false,
            pagingFlow.loadNextPageWithResult(PaginationDirection.UP).asSuccess().hasNext
        )

        pagingFlow.testLoadEverything(
            listOf(testDataSource),
            pageSize,
            pagingPresenter = presenter
        )
    }

    fun getItemsWithPagesOffset(pagesOffset: Int): Pair<Int, Int> {
        val startIndex = totalCount - (pageSize * (removePagesOffset + pagesOffset))
        val endIndex = totalCount - (pagesOffset * pageSize)
        return startIndex to endIndex
    }

    @Test
    fun loadBothDirectionsWithNullsTest() = runTest {
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                enableDroppedPagesNullPlaceholders = true
            )
        ) {
            addDataSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter(throttleDurationMs = 0)
        pagingFlow.testLoadEverything(
            listOf(testDataSource),
            pageSize,
            pagingPresenter = presenter
        )

        assertEquals(totalCount / pageSize, pagingFlow.currentPagesCount)
        val loadedData = presenter.dataFlow.value
        assertEquals(
            buildListOfNulls(totalCount - (pageSize * removePagesOffset)) +
                    testDataSource.getItems(totalCount).takeLast(pageSize * removePagesOffset),
            loadedData
        )

        var hasNext = true
        while (hasNext) {
            hasNext = pagingFlow.loadNextPageWithResult(PaginationDirection.UP).asSuccess().hasNext
        }

        pagingFlow.testLoadEverything(
            listOf(testDataSource),
            pageSize,
            pagingPresenter = presenter
        )
    }

    private fun buildListOfNulls(count: Int) = buildList {
        repeat(count) {
            add(null)
        }
    }
}
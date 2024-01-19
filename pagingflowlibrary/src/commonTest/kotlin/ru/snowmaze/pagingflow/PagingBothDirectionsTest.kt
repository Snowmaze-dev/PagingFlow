package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class PagingBothDirectionsTest {

    val loadSize = 10
    val removePagesOffset = 4
    val totalCount = loadSize * (removePagesOffset + 3)

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(loadSize, 0),
        removePagesOffset = removePagesOffset,
        mainDispatcher = Dispatchers.Unconfined,
        processingDispatcher = Dispatchers.Unconfined,
        shouldFillRemovedPagesWithNulls = false
    )

    @Test
    fun loadBothDirectionsTest() = runBlocking {
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(testDataSource)
        }
        pagingFlow.testLoadEverything(listOf(testDataSource), loadSize, shouldTestItems = false)

        assertEquals(removePagesOffset, pagingFlow.currentPagesCount)
        var loadedData = pagingFlow.dataFlow.value
        assertEquals(
            testDataSource.getItems(totalCount).takeLast(loadSize * removePagesOffset),
            loadedData
        )
        println(pagingFlow.loadNextPageWithResult(PaginationDirection.UP))
        loadedData = pagingFlow.dataFlow.value
        assertEquals(testDataSource.getItems(loadSize * removePagesOffset), loadedData)
        repeat(removePagesOffset) {
            pagingFlow.loadNextPageWithResult(PaginationDirection.UP)
        }
        println("loadNextPageWithResult ${pagingFlow.dataFlow.value}")
        assertEquals(testDataSource.getItems(loadSize * removePagesOffset), loadedData)
        assertEquals(
            false,
            pagingFlow.loadNextPageWithResult(PaginationDirection.UP).asSuccess().hasNext
        )
    }

    @Test
    fun loadBothDirectionsWithNullsTest() = runBlocking {
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                shouldFillRemovedPagesWithNulls = true
            )
        ) {
            addDataSource(testDataSource)
        }
        val removePagesOffsetSize = removePagesOffset * loadSize
        pagingFlow.testLoadEverything(listOf(testDataSource), loadSize, shouldTestItems = false)

        assertEquals(totalCount / loadSize, pagingFlow.currentPagesCount)
        val loadedData = pagingFlow.dataFlow.value
        println("$loadedData")
        val allItems = testDataSource.getItems(totalCount)
        assertEquals(
            buildListOfNulls(totalCount - removePagesOffsetSize) + allItems.takeLast(removePagesOffsetSize),
            loadedData
        )
        val result = pagingFlow.loadNextPageWithResult(paginationDirection = PaginationDirection.UP)
        println("${pagingFlow.dataFlow.value}")
        assertEquals(
            allItems.take(removePagesOffsetSize),
            pagingFlow.dataFlow.value
        )
    }

    private fun buildListOfNulls(count: Int) = buildList {
        repeat(count) {
            add(null)
        }
    }
}
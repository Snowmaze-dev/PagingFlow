package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.invoke
import kotlinx.coroutines.test.runTest
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.sources.MaxItemsConfiguration
import ru.snowmaze.pagingflow.sources.TestDataSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PagingBothDirectionsTest {

    val pageSize = 20
    val removePagesOffset = 4
    val totalCount = 10000

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        processingDispatcher = testDispatcher,
        maxItemsConfiguration = MaxItemsConfiguration(
            maxItemsCount = removePagesOffset * pageSize,
            enableDroppedPagesNullPlaceholders = false
        )
    )

    @Test
    fun testAsyncLoad() = runTest {
        val totalCount = 1000
        val testDataSource = TestDataSource(totalCount, 50)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                processingDispatcher = Dispatchers.Default,
                maxItemsConfiguration = basePagingFlowConfiguration.maxItemsConfiguration?.copy(
                    maxItemsCount = pageSize * 4,
                    enableDroppedPagesNullPlaceholders = false
                )
            )
        ) {
            addDataSource(TestDataSource(totalCount, 50L))
            addDataSource(TestDataSource(totalCount, 50L))
        }
        val presenter = pagingFlow.pagingDataPresenter(
            debounceBufferDurationMsProvider = { 10 },
        )

        var hasNext = true
        while (hasNext) {
           val result = pagingFlow.loadNextPageWithResult(
                PaginationDirection.DOWN,
                pagingParams = PagingParams {
                    put(PagingLibraryParamsKeys.ReturnAwaitJob, true)
                }
            ).asSuccess()
            hasNext = result.hasNext
            result.returnData[ReturnPagingLibraryKeys.DataSetJob].join()
            assertEquals(hasNext, pagingFlow.downPagingStatus.value.hasNextPage)
        }
        Dispatchers.Default { delay(10) }
        val maxItemsCount = pagingFlow.pagingFlowConfiguration.maxItemsConfiguration?.maxItemsCount!!
        assertTrue(
            maxItemsCount >= presenter.data.size,
            "expected $maxItemsCount but was ${presenter.data.size}"
        )
        assertContentEquals(
            testDataSource.getItems(totalCount).takeLast(presenter.data.size),
            presenter.data
        )
        hasNext = true
        while (hasNext) {
            hasNext =
                pagingFlow.loadNextPageWithResult(PaginationDirection.UP).asSuccess().hasNext
        }
        Dispatchers.Default { delay(10) }
        assertTrue(
            maxItemsCount >= presenter.data.size,
            "expected $maxItemsCount but was ${presenter.data.size}"
        )
    }

    @Test
    fun loadBothDirectionsTest() = runTest {
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter()
        pagingFlow.testLoadEverything(
            listOf(testDataSource),
            pagingPresenter = presenter
        )

        assertEquals(removePagesOffset, pagingFlow.currentPagesCount)
        var loadedData = presenter.data
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
            loadedData = presenter.data
            val (startIndex, endIndex) = getItemsWithPagesOffset(countOfPages)
            assertEquals(
                allItems.subList(startIndex, endIndex),
                loadedData
            )
        }
        loadedData = presenter.data
        assertEquals(testDataSource.getItems(pageSize * removePagesOffset), loadedData)
        assertTrue(pagingFlow.loadNextPageWithResult(
            PaginationDirection.UP
        ) is LoadNextPageResult.NothingToLoad)

        pagingFlow.testLoadEverything(
            listOf(testDataSource),
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
                maxItemsConfiguration = basePagingFlowConfiguration.maxItemsConfiguration?.copy(
                    enableDroppedPagesNullPlaceholders = true
                )
            )
        ) {
            addDataSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter()
        pagingFlow.testLoadEverything(
            listOf(testDataSource),
            pagingPresenter = presenter
        )

        assertEquals(totalCount / pageSize, pagingFlow.currentPagesCount)
        val loadedData = presenter.data
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
            pagingPresenter = presenter
        )
    }

    @Test
    fun loadSmallPagesTest() = runTest {
        val testDataSource = TestDataSource(totalCount)
        var currentLoadParams = LoadParams<Int>(2)
        val maxItemsCount = 5
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                defaultParamsProvider = { currentLoadParams },
                maxItemsConfiguration = basePagingFlowConfiguration.maxItemsConfiguration?.copy(
                    maxItemsCount = maxItemsCount
                )
            )
        ) {
            addDataSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter()
        pagingFlow.loadNextPageWithResult()
        assertEquals(2, presenter.data.size)
        repeat(2) {
            pagingFlow.loadNextPageWithResult()
        }
        assertEquals(4, presenter.data.size)
        currentLoadParams = LoadParams(1)
        pagingFlow.loadNextPageWithResult()
        assertEquals(5, presenter.data.size)
        pagingFlow.loadNextPageWithResult()
        assertEquals(4, presenter.data.size)
    }

    private fun buildListOfNulls(count: Int) = buildList {
        repeat(count) {
            add(null)
        }
    }
}
package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.source.MaxItemsConfiguration
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PagingForwardsAndThenBackwardsTest {

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

    private val randomDelay = {
        Random.nextLong(10, 100)
    }

    /**
     * Tests library ability to work in multi thread environment
     */
    @Test
    fun testAsyncLoad() = runTestOnDispatchersDefault {
        val totalCount = 1000
        val testDataSource = TestPagingSource(totalCount, randomDelay)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                processingDispatcher = Dispatchers.Default,
                maxItemsConfiguration = basePagingFlowConfiguration.maxItemsConfiguration?.copy(
                    maxItemsCount = pageSize * 4,
                    enableDroppedPagesNullPlaceholders = false
                ),
                shouldCollectOnlyNew = true
            )
        ) {
            addPagingSource(TestPagingSource(totalCount, randomDelay))
            addPagingSource(TestPagingSource(totalCount, randomDelay))
        }
        val presenter = pagingFlow.pagingDataPresenter(
            eventsBatchingDurationMsProvider = { 20 },
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
            if (!hasNext) result.returnData[ReturnPagingLibraryKeys.DataSetJob].join()
            assertEquals(hasNext, pagingFlow.downPagingStatus.value.hasNextPage)
        }
        val maxItemsCount =
            pagingFlow.pagingFlowConfiguration.maxItemsConfiguration?.maxItemsCount!!
        presenter.dataFlow.firstWithTimeout(
            message = {
                "expected less count than $maxItemsCount but was ${it?.size}"
            }
        ) { maxItemsCount >= it.size }
        assertContentEquals(
            testDataSource.getItems(totalCount).takeLast(presenter.data.size),
            presenter.data
        )
        hasNext = true
        while (hasNext) {
            hasNext =
                pagingFlow.loadNextPageWithResult(PaginationDirection.UP).asSuccess().hasNext
        }
        presenter.dataFlow.firstWithTimeout(
            message = {
                "expected less count than $maxItemsCount but was ${it?.size}"
            }
        ) { maxItemsCount >= it.size }
    }

    @Test
    fun loadBothDirectionsTest() = runTest {
        val testDataSource = TestPagingSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addPagingSource(testDataSource)
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
        assertTrue(
            pagingFlow.loadNextPageWithResult(
                PaginationDirection.UP
            ) is LoadNextPageResult.NothingToLoad
        )

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
        val testDataSource = TestPagingSource(totalCount)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                maxItemsConfiguration = basePagingFlowConfiguration.maxItemsConfiguration?.copy(
                    enableDroppedPagesNullPlaceholders = true
                )
            )
        ) {
            addPagingSource(testDataSource)
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
        val testDataSource = TestPagingSource(totalCount)
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
            addPagingSource(testDataSource)
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
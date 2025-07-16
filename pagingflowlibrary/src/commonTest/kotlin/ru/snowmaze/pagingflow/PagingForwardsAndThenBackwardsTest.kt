package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.presenters.BasicPresenterConfiguration
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.list.DiffListBuildStrategy
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.presenters.statePresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PagingForwardsAndThenBackwardsTest {

    private val pageSize = 20
    private val removePagesOffset = 4
    private val totalCount = 10000

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
     * Tests library ability to load data in both directions in multi thread environment
     */
    @Test
    fun testAsyncLoadBothDirections() = runTestOnDispatchersDefault {
        val totalCount = 1000
        val testDataSource = TestPagingSource(totalCount, randomDelay)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                processingContext = Dispatchers.Default,
                maxItemsConfiguration = basePagingFlowConfiguration.maxItemsConfiguration?.copy(
                    maxItemsCount = pageSize * 4,
                    enableDroppedPagesNullPlaceholders = false
                ),
                collectOnlyLatest = true,
                storePageItems = false
            )
        ) {
            addDownPagingSource(TestPagingSource(totalCount, randomDelay))
            addDownPagingSource(TestPagingSource(totalCount, randomDelay))
        }
        val presenter = pagingFlow.pagingDataPresenter(
            configuration = BasicPresenterConfiguration(listBuildStrategy = DiffListBuildStrategy()),
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
            if (!hasNext) withTimeout(5000L) {
                result.returnData[ReturnPagingLibraryKeys.DataSetJob].join()
            }
            assertEquals(hasNext, pagingFlow.downPagingStatus.value.hasNextPage)
        }
        val maxItemsCount =
            pagingFlow.pagingFlowConfiguration.maxItemsConfiguration?.maxItemsCount!!
        presenter.dataFlow.firstWithTimeout(
            message = {
                "expected less count than $maxItemsCount but was ${it?.size}"
            }
        ) { maxItemsCount >= it.size }
        presenter.dataFlow.firstEqualsWithTimeout(
            testDataSource.getItems(totalCount).takeLast(maxItemsCount)
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
    fun loadForwardsAndThenBackwards() = runTest {
        val testDataSource = TestPagingSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDownPagingSource(testDataSource)
        }
        val presenter = pagingFlow.statePresenter(sharingStarted = SharingStarted.Eagerly)
        pagingFlow.testLoadEverything(
            listOf(testDataSource),
            pagingPresenter = presenter
        )

        assertEquals(removePagesOffset, pagingFlow.pagesCount)
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

    @Test
    fun loadForwardsAndThenBackwardsWithNullPlaceholdersTest() = runTest {
        val testDataSource = TestPagingSource(totalCount)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                maxItemsConfiguration = basePagingFlowConfiguration.maxItemsConfiguration?.copy(
                    enableDroppedPagesNullPlaceholders = true
                )
            )
        ) {
            addDownPagingSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter().statePresenter(
            sharingStarted = SharingStarted.Eagerly
        )
        pagingFlow.testLoadEverything(
            listOf(testDataSource),
            pagingPresenter = presenter
        )

        assertEquals(totalCount / pageSize, pagingFlow.pagesCount)
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
            addDownPagingSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter().statePresenter(
            sharingStarted = SharingStarted.Eagerly
        )
        pagingFlow.loadNextPageWithResult()
        assertEquals(testDataSource.getItems(2), presenter.data)
        pagingFlow.loadNextPageWithResult()
        assertEquals(testDataSource.getItems(4), presenter.data)
        pagingFlow.loadNextPageWithResult()
        assertEquals(testDataSource.getItems(6).drop(2), presenter.data)
        currentLoadParams = LoadParams(1)
        pagingFlow.loadNextPageWithResult()
        assertEquals(testDataSource.getItems(7).drop(2), presenter.data)
        pagingFlow.loadNextPageWithResult()
        assertEquals(testDataSource.getItems(8).drop(4), presenter.data)
    }

    private fun buildListOfNulls(count: Int) = buildList {
        repeat(count) {
            add(null)
        }
    }

    private fun getItemsWithPagesOffset(pagesOffset: Int): Pair<Int, Int> {
        val startIndex = totalCount - (pageSize * (removePagesOffset + pagesOffset))
        val endIndex = totalCount - (pagesOffset * pageSize)
        return startIndex to endIndex
    }
}
package ru.snowmaze.pagingflow

import kotlinx.coroutines.test.runTest
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.PagingDataPresenter
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.sources.MaxItemsConfiguration
import ru.snowmaze.pagingflow.sources.TestDataSource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BasicPagingFlowTest {

    val pageSize = Random.nextInt(5, 30)

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams<Int>(pageSize),
        processingDispatcher = testDispatcher
    )

    @Test
    fun basePaginationUseCaseTest() = runTest {
        val totalCount = Random.nextInt(80, 1000)
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter()

        pagingFlow.testLoadEverything(listOf(testDataSource), pagingPresenter = presenter)
        invalidateAndCheckLoadingRight(pagingFlow, testDataSource, pagingDataPresenter = presenter)
    }

    @Test
    fun testThreePaginationForwardAndThenBackwards() = runTest {
        val testDataSource = TestDataSource(pageSize * 3)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                maxItemsConfiguration = MaxItemsConfiguration(
                    pageSize * 2,
                    enableDroppedPagesNullPlaceholders = false
                ),
            )
        ) {
            addDataSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter()
        pagingFlow.loadNextPageWithResult()
        pagingFlow.loadNextPageWithResult()
        pagingFlow.loadNextPageWithResult()
        assertContentEquals(
            testDataSource.getItems(testDataSource.totalCount).takeLast(pageSize * 2),
            presenter.data
        )
        pagingFlow.loadNextPageWithResult(PaginationDirection.UP)
        assertContentEquals(
            testDataSource.getItems(pageSize * 2),
            presenter.data
        )
    }

    @Test
    fun paginationErrorTest() = runTest {
        val totalCount = Random.nextInt(80, 1000)
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(testDataSource)
        }
        testDataSource.currentException = IllegalArgumentException()
        val result = pagingFlow.loadNextPageWithResult()
        assertTrue(result is LoadNextPageResult.Failure<Int, String>)
        assertTrue(pagingFlow.downPagingStatus.value is PagingStatus.Failure)
    }

    @Test
    fun baseThreeSourcesPaginationUseCaseTest() = runTest {
        val firstTestDataSource = TestDataSource(Random.nextInt(80, 500))
        val secondTestDataSource = TestDataSource(Random.nextInt(80, 500))
        val thirdTestDataSource = TestDataSource(Random.nextInt(80, 500))
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(firstTestDataSource)
            addDataSource(secondTestDataSource)
            addDataSource(thirdTestDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter(
            invalidateBehavior = InvalidateBehavior.INVALIDATE_IMMEDIATELY
        )
        var hasNext = true
        while (hasNext) {
            hasNext = pagingFlow.loadNextPageWithResult().asSuccess().hasNext
        }
        assertEquals(
            firstTestDataSource.totalCount + secondTestDataSource.totalCount + thirdTestDataSource.totalCount,
            presenter.data.size
        )
        pagingFlow.invalidate()

        pagingFlow.testLoadEverything(
            listOf(firstTestDataSource, secondTestDataSource, thirdTestDataSource),
            pagingPresenter = presenter
        )
        assertTrue(pagingFlow.loadNextPageWithResult() is LoadNextPageResult.NothingToLoad)
        invalidateAndCheckLoadingRight(pagingFlow, firstTestDataSource, presenter)
    }

    private suspend fun invalidateAndCheckLoadingRight(
        pagingFlow: PagingFlow<Int, String>,
        firstSource: TestDataSource,
        pagingDataPresenter: PagingDataPresenter<Int, String>,
    ) {
        pagingFlow.invalidate()
        val resultAfterValidate = pagingFlow.loadNextPageWithResult()
        assertEquals(true, resultAfterValidate.asSuccess().hasNext)
        assertEquals(firstSource.getItems(pageSize), pagingDataPresenter.data)
    }
}
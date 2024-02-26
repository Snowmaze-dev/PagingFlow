package ru.snowmaze.pagingflow

import kotlinx.coroutines.test.runTest
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.PagingDataPresenter
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BasicPagingFlowTest {

    val pageSize = Random.nextInt(5, 30)

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        maxItemsCount = null,
        processingDispatcher = testDispatcher
    )

    @Test
    fun basePaginationUseCaseTest() = runTest {
        val totalCount = Random.nextInt(80, 1000)
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter(throttleDurationMs = 0)

        pagingFlow.testLoadEverything(listOf(testDataSource), pagingPresenter = presenter)
        invalidateAndCheckLoadingRight(pagingFlow, testDataSource, pagingDataPresenter = presenter)
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
        assertTrue(pagingFlow.downPagingStatus.value is PagingStatus.Failure<DefaultPagingStatus>)
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
            throttleDurationMs = 0,
            invalidateBehavior = InvalidateBehavior.INVALIDATE_IMMEDIATELY
        )
        var hasNext = true
        while (hasNext) {
            hasNext = pagingFlow.loadNextPageWithResult().asSuccess().hasNext
        }
        assertEquals(
            firstTestDataSource.totalCount + secondTestDataSource.totalCount + thirdTestDataSource.totalCount,
            presenter.dataFlow.value.size
        )
        pagingFlow.invalidate()

        pagingFlow.testLoadEverything(
            listOf(firstTestDataSource, secondTestDataSource, thirdTestDataSource),
            pagingPresenter = presenter
        )
        assertFalse(pagingFlow.loadNextPageWithResult().asSuccess().hasNext)
        invalidateAndCheckLoadingRight(pagingFlow, firstTestDataSource, presenter)
    }

    private suspend fun invalidateAndCheckLoadingRight(
        pagingFlow: PagingFlow<Int, String, DefaultPagingStatus>,
        firstSource: TestDataSource,
        pagingDataPresenter: PagingDataPresenter<Int, String>,
    ) {
        pagingFlow.invalidate()
        val resultAfterValidate = pagingFlow.loadNextPageWithResult()
        assertEquals(true, resultAfterValidate.asSuccess().hasNext)
        assertEquals(firstSource.getItems(pageSize), pagingDataPresenter.dataFlow.value)
    }
}
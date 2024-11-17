package ru.snowmaze.pagingflow

import kotlinx.coroutines.test.runTest
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.PagingDataPresenter
import ru.snowmaze.pagingflow.presenters.SimplePresenterConfiguration
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.source.MaxItemsConfiguration
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BasicPagingFlowTest {

    val pageSize = 30
//    val pageSize = Random.nextInt(5, 30)

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams<Int>(pageSize),
        processingDispatcher = testDispatcher
    )

    @Test
    fun basePaginationUseCaseTest() = runTest {
        val totalCount = Random.nextInt(80, 1000)
        val testDataSource = TestPagingSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addPagingSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter()

        pagingFlow.testLoadEverything(listOf(testDataSource), pagingPresenter = presenter)
        invalidateAndCheckLoadingRight(pagingFlow, testDataSource, pagingDataPresenter = presenter)
    }

    @Test
    fun testThreePaginationForwardAndThenBackwards() = runTest {
        val testDataSource = TestPagingSource(pageSize * 3)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                maxItemsConfiguration = MaxItemsConfiguration(
                    pageSize * 2,
                    enableDroppedPagesNullPlaceholders = false
                ),
            )
        ) {
            addPagingSource(testDataSource)
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
        val testDataSource = TestPagingSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addPagingSource(testDataSource)
        }
        testDataSource.currentException = IllegalArgumentException()
        val result = pagingFlow.loadNextPageWithResult()
        assertTrue(result is LoadNextPageResult.Failure<Int, String>)
        assertTrue(pagingFlow.downPagingStatus.value is PagingStatus.Failure)
    }

    @Test
    fun baseThreeSourcesPaginationUseCaseTest() = runTest {
        val firstTestDataSource = TestPagingSource(Random.nextInt(80, 500))
        val secondTestDataSource = TestPagingSource(Random.nextInt(80, 500))
        val thirdTestDataSource = TestPagingSource(Random.nextInt(80, 500))
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addPagingSource(firstTestDataSource)
            addPagingSource(secondTestDataSource)
            addPagingSource(thirdTestDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter(
            SimplePresenterConfiguration(invalidateBehavior = InvalidateBehavior.INVALIDATE_IMMEDIATELY)
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
        firstSource: TestPagingSource,
        pagingDataPresenter: PagingDataPresenter<Int, String>,
    ) {
        pagingFlow.invalidate()
        val resultAfterValidate = pagingFlow.loadNextPageWithResult()
        assertEquals(true, resultAfterValidate.asSuccess().hasNext)
        assertEquals(firstSource.getItems(pageSize), pagingDataPresenter.data)
    }
}
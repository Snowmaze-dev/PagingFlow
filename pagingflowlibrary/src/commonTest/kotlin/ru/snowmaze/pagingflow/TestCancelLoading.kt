package ru.snowmaze.pagingflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TestCancelLoading {

    private val pageSize = Random.nextInt(5, 30)

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        processingDispatcher = Dispatchers.Default,
    )

    @Test
    fun baseTestCancelLoading() = runTestOnDispatchersDefault {
        val totalCount = Random.nextInt(80, 1000)
        var currentDelay = Long.MAX_VALUE
        val testDataSource = TestPagingSource(totalCount, loadDelay = {
            currentDelay
        })
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            assertFalse(downPagingStatus.value.hasNextPage)
            addDownPagingSource(testDataSource)
            assertTrue(downPagingStatus.value.hasNextPage)
        }
        val presenter = pagingFlow.pagingDataPresenter()
        val job = scope.launch {
            pagingFlow.loadNextPageWithResult()
        }
        delay(5L)
        job.cancel()
        currentDelay = 0L
        assertIs<LoadNextPageResult.Success<Int>>(pagingFlow.loadNextPageWithResult())
    }
}
package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.sources.TestDataSource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonPresentersTest {

    val pageSize = Random.nextInt(5, 30)

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        processingDispatcher = testDispatcher
    )

    @Test
    fun asyncBehaviorPresenterTest() = runTestOnDispatchersDefault {
        val totalCount = Random.nextInt(80, 1000)
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                processingDispatcher = Dispatchers.Default
            )
        ) {
            addDataSource(testDataSource)
        }
        val presenter = pagingFlow.pagingDataPresenter(
            invalidateBehavior = InvalidateBehavior.INVALIDATE_IMMEDIATELY
        )

        pagingFlow.loadNextPageWithResult()
        delay(30L)
        assertEquals(
            pageSize,
            presenter.data.size
        )
        pagingFlow.invalidate()
        delay(30L)
        assertEquals(
            0,
            presenter.data.size
        )
    }
}
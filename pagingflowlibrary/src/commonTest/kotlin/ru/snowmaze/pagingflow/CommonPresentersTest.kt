package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import ru.snowmaze.pagingflow.diff.flow.asFlow
import ru.snowmaze.pagingflow.diff.flow.asPagingEventsMedium
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.BasicPresenterConfiguration
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.presenters.statePresenter
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonPresentersTest {

    val pageSize = Random.nextInt(5, 30)

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        processingDispatcher = testDispatcher,
    )

    @Test
    fun asyncBehaviorPresenterTest() = runTestOnDispatchersDefault {
        val totalCount = Random.nextInt(80, 1000)
        val testDataSource = TestPagingSource(totalCount)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                processingContext = Dispatchers.Default,
                collectOnlyLatest = true
            )
        ) {
            addDownPagingSource(testDataSource)
        }
        val presenter = pagingFlow
            .asFlow()
            .asPagingEventsMedium(pagingFlow.config)
            .pagingDataPresenter(
                configuration = BasicPresenterConfiguration(
                    invalidateBehavior = InvalidateBehavior.INVALIDATE_IMMEDIATELY
                )
            ).statePresenter(sharingStarted = SharingStarted.Eagerly)

        pagingFlow.loadNextPageAndAwaitDataSet()
        assertEquals(
            pageSize,
            presenter.data.size
        )
        pagingFlow.invalidate(removeCachedData = true)
        assertEquals(
            0,
            presenter.data.size
        )
    }
}
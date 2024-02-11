package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.invoke
import kotlinx.coroutines.test.runTest
import ru.snowmaze.pagingflow.diff.mediums.MappingPagingDataMedium
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.composite.CompositePagingPresenterBuilder
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
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
    fun compositePresenterTest() = runTest {
        val totalCount = Random.nextInt(80, 1000)
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(testDataSource)
        }
        val prependItems = listOf(-1, -5)
        val presenter = CompositePagingPresenterBuilder.create(
            pagingDataChangesMedium = MappingPagingDataMedium(pagingFlow) { event ->
                event.items.mapIndexed { _: Int, s: String? ->
                    s?.let { s.split(" ")[1].toInt() }
                }
            },
            invalidateBehavior = InvalidateBehavior.INVALIDATE_IMMEDIATELY
        ) {
            section { prependItems }
            dataSourceSection(0)
        }

        pagingFlow.testLoadEverything(
            dataSources = listOf(testDataSource),
            pagingPresenter = pagingFlow.pagingDataPresenter()
        )
        assertEquals(
            prependItems + List(totalCount) { index: Int -> index },
            presenter.dataFlow.value
        )
        pagingFlow.invalidate()
        assertEquals(
            prependItems,
            presenter.dataFlow.value
        )
    }

    @Test
    fun asyncBehaviorPresenterTest() = runTest {
        val totalCount = Random.nextInt(80, 1000)
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                processingDispatcher = Dispatchers.Default
            )
        ) {
            addDataSource(testDataSource)
        }
        val presenter =
            pagingFlow.pagingDataPresenter(invalidateBehavior = InvalidateBehavior.INVALIDATE_IMMEDIATELY)

        pagingFlow.loadNextPageWithResult()
        Dispatchers.Default { delay(30L) }
        assertEquals(
            pageSize,
            presenter.dataFlow.value.size
        )
        pagingFlow.invalidate()
        Dispatchers.Default { delay(30L) }
        assertEquals(
            0,
            presenter.dataFlow.value.size
        )
    }
}
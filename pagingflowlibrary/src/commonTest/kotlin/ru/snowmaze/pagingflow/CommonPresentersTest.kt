package ru.snowmaze.pagingflow

import kotlinx.coroutines.test.runTest
import ru.snowmaze.pagingflow.diff.mediums.PagingDataMappingMedium
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
        mainDispatcher = testDispatcher,
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
            dataChangesMedium = PagingDataMappingMedium(pagingFlow) {
                it.mapIndexed { index: Int, s: String? ->
                    s?.let { s.split(" ")[1].toInt() }
                }
            },
            coroutineScope = pagingFlow.pagingFlowConfiguration.coroutineScope,
            processingDispatcher = testDispatcher,
            invalidateBehavior = InvalidateBehavior.INVALIDATE_IMMEDIATELY
        ) {
            section { prependItems }
            dataSourceSection(0)
        }

        pagingFlow.testLoadEverything(
            dataSources = listOf(testDataSource),
            pageSize = pageSize,
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
}
package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import ru.snowmaze.pagingflow.diff.mediums.DataSourceDataChangesMedium
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.presenters.statePresenter
import ru.snowmaze.pagingflow.source.MaxItemsConfiguration
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SpecificDataSourceMediumTest {

    private val pageSize = 20
    private val removePagesOffset = 2
    private val totalCount = 10000

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        processingDispatcher = Dispatchers.Default,
        maxItemsConfiguration = MaxItemsConfiguration(
            maxItemsCount = removePagesOffset * pageSize, enableDroppedPagesNullPlaceholders = false
        )
    )

    @Test
    fun baseTest() = runTestOnDispatchersDefault {
        val testDataSource = TestPagingSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDownPagingSource(TestPagingSource(40))
            addDownPagingSource(testDataSource)
        }
        val presenterForFirst = DataSourceDataChangesMedium<Int, String, String>(
            pagingFlow,
            0
        ).pagingDataPresenter().statePresenter()
        val presenterForSecond = DataSourceDataChangesMedium<Int, String, String>(
            pagingFlow,
            1
        ).pagingDataPresenter().statePresenter()
        pagingFlow.loadNextPageWithResult()
        presenterForSecond.dataFlow.firstEqualsWithTimeout(emptyList())
        assertEquals(testDataSource.getItems(20), presenterForFirst.data)
        pagingFlow.loadNextPageWithResult()
        presenterForFirst.dataFlow.firstEqualsWithTimeout(testDataSource.getItems(40))
        presenterForSecond.dataFlow.firstEqualsWithTimeout(emptyList())
        pagingFlow.loadNextPageWithResult()
        presenterForFirst.dataFlow.firstEqualsWithTimeout(testDataSource.getItems(40).takeLast(20))
        presenterForSecond.dataFlow.firstEqualsWithTimeout(testDataSource.getItems(20))
    }
}
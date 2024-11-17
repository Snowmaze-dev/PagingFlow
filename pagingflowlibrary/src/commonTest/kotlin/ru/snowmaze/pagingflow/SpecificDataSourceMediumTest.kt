package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import ru.snowmaze.pagingflow.diff.mediums.DataSourceDataChangesMedium
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
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
            addPagingSource(TestPagingSource(40))
            addPagingSource(testDataSource)
        }
        val presenterForFirst = DataSourceDataChangesMedium<Int, String, String>(
            pagingFlow,
            0
        ).pagingDataPresenter()
        val presenterForSecond = DataSourceDataChangesMedium<Int, String, String>(
            pagingFlow,
            1
        ).pagingDataPresenter()
        pagingFlow.loadNextPageWithResult()
        assertEquals(testDataSource.getItems(20), presenterForFirst.data)
        assertEquals(emptyList(), presenterForSecond.latestData.data)
        pagingFlow.loadNextPageWithResult()
        assertEquals(testDataSource.getItems(40), presenterForFirst.data)
        assertEquals(emptyList(), presenterForSecond.latestData.data)
        pagingFlow.loadNextPageWithResult()
        assertEquals(testDataSource.getItems(40).takeLast(20), presenterForFirst.data)
        assertEquals(testDataSource.getItems(20), presenterForSecond.data)
    }
}
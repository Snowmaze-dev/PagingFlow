package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import ru.snowmaze.pagingflow.diff.mediums.DataSourceDataChangesMedium
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.sources.MaxItemsConfiguration
import ru.snowmaze.pagingflow.sources.TestDataSource
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
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(TestDataSource(40))
            addDataSource(testDataSource)
        }
        val presenterForFirst = DataSourceDataChangesMedium<Int, String, String>(
            pagingFlow,
            0
        ).pagingDataPresenter()
        val presenterForSecond = DataSourceDataChangesMedium<Int, String, String>(
            pagingFlow,
            1
        ).pagingDataPresenter()
        pagingFlow.loadNextPageAndAwaitDataSet()
        assertEquals(presenterForFirst.latestData.data, testDataSource.getItems(20))
        assertEquals(emptyList(), presenterForSecond.latestData.data)
        pagingFlow.loadNextPageAndAwaitDataSet()
        assertEquals(presenterForFirst.latestData.data, testDataSource.getItems(40))
        assertEquals(emptyList(), presenterForSecond.latestData.data)
        pagingFlow.loadNextPageAndAwaitDataSet()
        delay(10)

        assertEquals(testDataSource.getItems(40).takeLast(20), presenterForFirst.latestData.data)
        assertEquals(testDataSource.getItems(20), presenterForSecond.latestData.data)
    }
}
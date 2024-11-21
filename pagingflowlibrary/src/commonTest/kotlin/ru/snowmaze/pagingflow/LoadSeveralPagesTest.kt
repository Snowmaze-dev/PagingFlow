package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.source.MaxItemsConfiguration
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoadSeveralPagesTest {

    private val pageSize = 20
    private val removePagesOffset = 4

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        processingDispatcher = testDispatcher,
        maxItemsConfiguration = MaxItemsConfiguration(
            maxItemsCount = removePagesOffset * pageSize,
            enableDroppedPagesNullPlaceholders = false
        )
    )

    // TODO add testing paging flow with multiple paging sources first of which returns nothing to load
    @Test
    fun testLoadSeveralPages() = runTestOnDispatchersDefault {
        val totalCount = 1000
        val source = TestPagingSource(totalCount)
        val maxItems = pageSize * 4
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                processingDispatcher = Dispatchers.Default,
                maxItemsConfiguration = basePagingFlowConfiguration.maxItemsConfiguration?.copy(
                    maxItemsCount = maxItems,
                    enableDroppedPagesNullPlaceholders = false
                )
            )
        ) {
            addDownPagingSource(source)
        }
        val presenter = pagingFlow.pagingDataPresenter(
            eventsBatchingDurationMsProvider = { 10 },
        )
        var pages = 0
        val result = pagingFlow.loadSeveralPages(
            getPagingParams = {
                pages++
                PagingParams(PagingLibraryParamsKeys.ReturnAwaitJob to true).takeUnless { pages > 2 }
            },
        )
        assertTrue(pagingFlow.downPagingStatus.value.hasNextPage)
        assertFalse(pagingFlow.upPagingStatus.value.hasNextPage)
        assertEquals(
            2,
            result.returnData[ReturnPagingLibraryKeys.PagingParamsList].mapNotNull {
                it?.getOrNull(ReturnPagingLibraryKeys.DataSetJob)
            }.size
        )
        presenter.dataFlow.firstWithTimeout { it.size == pageSize * 2 }

        pages = 0
        pagingFlow.loadSeveralPages(
            getPagingParams = {
                pages++
                if (pages > 4) null
                else PagingParams(PagingLibraryParamsKeys.ReturnAwaitJob to true)
            },
        )
        presenter.dataFlow.firstWithTimeout {
            source.getItems(pageSize * 6).takeLast(maxItems) == it
        }
    }
}
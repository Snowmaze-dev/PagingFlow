package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import ru.snowmaze.pagingflow.params.LoadSeveralPagesData
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.sources.MaxItemsConfiguration
import ru.snowmaze.pagingflow.sources.TestDataSource
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun testLoadSeveralPages() = runTestOnDispatchersDefault {
        val totalCount = 100
        val source = TestDataSource(totalCount)
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
            addDataSource(source)
        }
        val presenter = pagingFlow.pagingDataPresenter(
            debounceBufferDurationMsProvider = { 10 },
        )
        var pages = 0
        val result = pagingFlow.loadSeveralPages(
            awaitDataSet = true,
            getPagingParams = {
                pages++
                PagingParams.EMPTY.takeUnless { pages > 2 }
            },
        )
        assertEquals(
            2,
            result.returnData[ReturnPagingLibraryKeys.PagingParamsList].mapNotNull {
                it?.getOrNull(ReturnPagingLibraryKeys.DataSetJob)
            }.size
        )
        assertEquals(source.getItems(pageSize * 2), presenter.data)

        pages = 0
        pagingFlow.loadSeveralPages(
            getPagingParams = {
                PagingParams(
                    PagingLibraryParamsKeys.LoadSeveralPages to LoadSeveralPagesData<Int, String>(
                        getPagingParams = {
                            pages++
                            if (pages > 4) null
                            else PagingParams(PagingLibraryParamsKeys.ReturnAwaitJob to true)
                        },
                    )
                )
            },
            awaitDataSet = true
        )
        delay(50)
        assertEquals(source.getItems(pageSize * 5).takeLast(maxItems), presenter.data)
    }
}
package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.invoke
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
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

    val pageSize = 20
    val removePagesOffset = 4

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        processingDispatcher = testDispatcher,
        maxItemsConfiguration = MaxItemsConfiguration(
            maxItemsCount = removePagesOffset * pageSize,
            enableDroppedPagesNullPlaceholders = false
        )
    )

    @Test
    fun testLoadSeveralPages() = runTest {
        val totalCount = 100
        val source = TestDataSource(totalCount, 0L)
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
        val jobs = pagingFlow.loadNextPageWithResult(
            pagingParams = PagingParams(
                PagingLibraryParamsKeys.LoadSeveralPages to LoadSeveralPagesData<Int, String>(
                    getPagingParams = {
                        pages++
                        if (pages > 2) null
                        else PagingParams(PagingLibraryParamsKeys.ReturnAwaitJob to true)
                    },
                    onSuccess = {
                    }
                )
            )
        ).returnData[ReturnPagingLibraryKeys.PagingParamsList].mapNotNull {
            it?.getOrNull(ReturnPagingLibraryKeys.DataSetJob)
        }
        assertEquals(2, jobs.size)
        jobs.joinAll()
        assertEquals(source.getItems(pageSize * 2), presenter.data)

        pages = 0
        pagingFlow.loadNextPageWithResult(
            pagingParams = PagingParams(
                PagingLibraryParamsKeys.LoadSeveralPages to LoadSeveralPagesData<Int, String>(
                    getPagingParams = {
                        pages++
                        if (pages > 4) null
                        else PagingParams(PagingLibraryParamsKeys.ReturnAwaitJob to true)
                    },
                )
            )
        ).returnData[ReturnPagingLibraryKeys.PagingParamsList].mapNotNull {
            it?.getOrNull(ReturnPagingLibraryKeys.DataSetJob)
        }.joinAll()
        Dispatchers.Default.invoke { delay(50) }
        assertEquals(source.getItems(pageSize * 5).takeLast(maxItems), presenter.data)
    }
}
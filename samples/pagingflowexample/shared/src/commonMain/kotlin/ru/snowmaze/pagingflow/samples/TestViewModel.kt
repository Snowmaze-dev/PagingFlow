package ru.snowmaze.pagingflow.samples

import dev.icerock.moko.mvvm.viewmodel.ViewModel
import kotlinx.coroutines.flow.collectLatest
import ru.snowmaze.pagingflow.ExperimentalPagingApi
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.MaxItemsConfiguration
import ru.snowmaze.pagingflow.PagingFlowConfiguration
import ru.snowmaze.pagingflow.buildPagingFlow
import ru.snowmaze.pagingflow.diff.batchEventsMedium
import ru.snowmaze.pagingflow.diff.compositeDataMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.composite.flowSection
import ru.snowmaze.pagingflow.diff.mediums.composite.mapFlowSection

class TestViewModel : ViewModel() {

    companion object {
        const val EXAMPLE_LOAD_SIZE = 50
        const val TOTAL_ITEMS_COUNT = 10000
        const val REMOVE_PAGE_OFFSET = 3
        const val PREFETCH_DISTANCE = 30
    }

    var totalItemsCount = TOTAL_ITEMS_COUNT

    val pagingFlow = buildPagingFlow(
        PagingFlowConfiguration(
            LoadParams(EXAMPLE_LOAD_SIZE, 0),
            maxItemsConfiguration = MaxItemsConfiguration(
                maxItemsCount = REMOVE_PAGE_OFFSET * EXAMPLE_LOAD_SIZE
            )
        ),
        loadFirstPage = true,
        TestPagingSource(totalItemsCount)
    )
    @OptIn(ExperimentalPagingApi::class)
    val pagingEventsMedium: PagingEventsMedium<Int, TestItem> = pagingFlow.compositeDataMedium {
        dataSourceSection(0) {
            it.map { item ->
                TestItem.Item(item)
            }
        }
        mapFlowSection(pagingFlow.downPagingStatus) {
            if (it.hasNextPage) listOf(TestItem.Loader(isDown = true))
            else null
        }
    }.batchEventsMedium(eventsBatchingDurationMsProvider = { 50L })
}
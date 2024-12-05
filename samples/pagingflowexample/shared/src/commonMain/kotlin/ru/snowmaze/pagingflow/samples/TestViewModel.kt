package ru.snowmaze.pagingflow.samples

import dev.icerock.moko.mvvm.viewmodel.ViewModel
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PagingFlowConfiguration
import ru.snowmaze.pagingflow.buildPagingFlow
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.loadNextPage
import ru.snowmaze.pagingflow.source.MaxItemsConfiguration

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
        TestPagingSource(totalItemsCount, true)
    )
    val pagingDataChangesMedium: PagingDataChangesMedium<Int, String> = pagingFlow
}
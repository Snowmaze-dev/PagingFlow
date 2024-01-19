package ru.snowmaze.pagingflow.samples

import dev.icerock.moko.mvvm.viewmodel.ViewModel
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PagingFlowConfiguration
import ru.snowmaze.pagingflow.buildPagingFlow

class TestViewModel : ViewModel() {

    companion object {
        const val EXAMPLE_LOAD_SIZE = 50
        const val TOTAL_ITEMS_COUNT = 10000
        const val REMOVE_PAGE_OFFSET = 3
        const val PREFETCH_DISTANCE = 10
    }

    var totalItemsCount = TOTAL_ITEMS_COUNT

    val pagingFlow by lazy {
        buildPagingFlow(
            PagingFlowConfiguration(
                LoadParams(EXAMPLE_LOAD_SIZE, 0),
                removePagesOffset = REMOVE_PAGE_OFFSET
            )
        ) {
            addDataSource(TestDataSource(totalItemsCount, true))
            loadNextPage()
        }
    }
}

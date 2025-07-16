package ru.snowmaze.pagingflow.samples

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.map
import ru.snowmaze.pagingflow.samples.TestViewModel.Companion.TOTAL_ITEMS_COUNT

class TestPagingViewModel : ViewModel() {

    val flow = Pager(
        // Configure how data is loaded by passing additional properties to
        // PagingConfig, such as prefetchDistance.
        PagingConfig(pageSize = 20, maxSize = 150)
    ) {
        TestPagingDataSource(TOTAL_ITEMS_COUNT)
    }.flow.cachedIn(viewModelScope).map { data ->
        data.map { TestItem.Item(it) }
    }
}
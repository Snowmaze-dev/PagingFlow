package ru.snowmaze.pagingflow.sample

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import ru.snowmaze.pagingflow.ExperimentalPagingApi
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.MaxItemsConfiguration
import ru.snowmaze.pagingflow.PagingFlowConfiguration
import ru.snowmaze.pagingflow.buildPagingFlow
import ru.snowmaze.pagingflow.diff.batchEventsMedium
import ru.snowmaze.pagingflow.diff.compositeListMedium
import ru.snowmaze.pagingflow.diff.map
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.composite.mapFlowSection

@Inject
@ViewModelKey(TestViewModel::class)
@ContributesIntoMap(AppScope::class)
class TestViewModel : ViewModel() {

    companion object {
        const val EXAMPLE_LOAD_SIZE = 50
        const val TOTAL_ITEMS_COUNT = 10000
        const val MAX_PLACEHOLDERS_PAGES = 4
        const val REMOVE_PAGE_OFFSET = 3
        const val PREFETCH_DISTANCE = 30
    }

    var totalItemsCount = TOTAL_ITEMS_COUNT

    val pagingFlow = buildPagingFlow(
        PagingFlowConfiguration(
            LoadParams(EXAMPLE_LOAD_SIZE, 0),
            maxItemsConfiguration = MaxItemsConfiguration(
                maxItemsCount = REMOVE_PAGE_OFFSET * EXAMPLE_LOAD_SIZE,
                maxDroppedPagesItemsCount = MAX_PLACEHOLDERS_PAGES * EXAMPLE_LOAD_SIZE,
                restoreDroppedNullPagesWhenNeeded = true
            )
        ),
        loadFirstPage = true,
        TestPagingSource(totalItemsCount)
    )

    @OptIn(ExperimentalPagingApi::class)
    val pagingEventsMedium: PagingEventsMedium<Int, TestItem> = pagingFlow.compositeListMedium {
        dataSourceSection(0) {
            it.map { item ->
                if (item == null) null else TestItem.Item(item)
            }
        }
        mapFlowSection(pagingFlow.downPagingStatus) {
            if (it.hasNextPage && pagingFlow.pagesCount != 0) listOf(TestItem.Loader(isDown = true))
            else emptyList()
        }
    }.batchEventsMedium(eventsBatchingDurationMsProvider = { 50L })
}
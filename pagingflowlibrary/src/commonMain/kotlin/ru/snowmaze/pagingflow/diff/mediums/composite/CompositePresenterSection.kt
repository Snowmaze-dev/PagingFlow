package ru.snowmaze.pagingflow.diff.mediums.composite

import kotlinx.coroutines.flow.Flow
import ru.snowmaze.pagingflow.diff.PageChangedEvent

internal sealed class CompositePresenterSection<Key : Any, Data : Any, NewData : Any> {

    var sourceIndex: Int = 0
    var pages: MutableList<PageChangedEvent<Key, NewData>> = mutableListOf()
    var firstPageIndex: Int = 0

    internal class SimpleSection<Key : Any, Data : Any, NewData : Any>(
        val updateWhenDataUpdated: Boolean,
        val itemsProvider: () -> List<NewData>,
    ) : CompositePresenterSection<Key, Data, NewData>()

    internal class FlowSection<Key : Any, Data : Any, NewData : Any>(
        val itemsFlow: Flow<List<NewData>>,
    ) : CompositePresenterSection<Key, Data, NewData>()

    internal class DataSourceSection<Key : Any, Data : Any, NewData : Any>(
        var dataSourceIndex: Int,
        val mapData: (List<Data>) -> List<NewData>,
    ) : CompositePresenterSection<Key, Data, NewData>()
}
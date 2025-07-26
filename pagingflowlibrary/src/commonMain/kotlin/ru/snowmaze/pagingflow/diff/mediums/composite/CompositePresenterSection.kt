package ru.snowmaze.pagingflow.diff.mediums.composite

import androidx.collection.MutableIntSet
import androidx.collection.MutableObjectList
import kotlinx.coroutines.flow.Flow
import ru.snowmaze.pagingflow.diff.PageChangedEvent

internal sealed class CompositePresenterSection<Key : Any, Data : Any, NewData : Any> {

    var sourceIndex: Int = 0
    var pages: MutableObjectList<PageChangedEvent<Key, NewData>> = MutableObjectList()
    val pagesList = pages.asList()
    var firstPageIndex: Int = 0

    internal class SimpleSection<Key : Any, Data : Any, NewData : Any>(
        val updateWhenDataUpdated: Boolean,
        val itemsProvider: () -> List<NewData?>,
    ) : CompositePresenterSection<Key, Data, NewData>()

    internal class FlowSection<Key : Any, Data : Any, NewData : Any>(
        val itemsFlow: Flow<List<NewData?>>,
    ) : CompositePresenterSection<Key, Data, NewData>()

    internal class DataSourceSection<Key : Any, Data : Any, NewData : Any>(
        var dataSourceIndex: Int,
        val mapData: (PageChangedEvent<Key, Data>) -> List<NewData?>,
    ) : CompositePresenterSection<Key, Data, NewData>() {

        var removedPagesNumbers = MutableIntSet()
    }
}
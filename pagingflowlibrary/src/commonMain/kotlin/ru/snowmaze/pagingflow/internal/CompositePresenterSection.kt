package ru.snowmaze.pagingflow.internal

import ru.snowmaze.pagingflow.diff.PageChangedEvent

internal sealed class CompositePresenterSection<Data: Any> {

    open var items: MutableMap<Int, PageChangedEvent<*, Data>> = mutableMapOf()

    internal class SimpleSection<Data : Any>(
        val itemsProvider: () -> List<Data>,
    ) : CompositePresenterSection<Data>()

    internal class DataSourceSection<Data : Any>(
        var dataSourceIndex: Int? = null
    ) : CompositePresenterSection<Data>()

}
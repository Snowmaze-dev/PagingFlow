package ru.snowmaze.pagingflow.internal

internal sealed class CompositePresenterSection<Data: Any> {

    open var items: MutableMap<Int, List<Data?>> = mutableMapOf()

    internal class SimpleSection<Data : Any>(
        val itemsProvider: () -> List<Data>,
    ) : CompositePresenterSection<Data>()

    internal class DataSourceSection<Data : Any>(
        var dataSourceIndex: Int? = null
    ) : CompositePresenterSection<Data>()

}
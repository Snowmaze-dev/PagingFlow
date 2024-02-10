package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.flow.StateFlow

internal sealed class CompositePresenterSection<Data : Any> {

    abstract val sectionIndex: Int
    abstract var pageIndex: Int

    internal class SimpleSection<Data : Any>(
        val itemsProvider: () -> List<Data>,
        override val sectionIndex: Int,
        override var pageIndex: Int = sectionIndex,
    ) : CompositePresenterSection<Data>() {

        var items: List<Data> = emptyList()
    }

    internal class FlowSimpleSection<Data : Any>(
        val flow: StateFlow<List<Data>>,
        override val sectionIndex: Int,
        override var pageIndex: Int = sectionIndex,
    ) : CompositePresenterSection<Data>()

    internal class DataSourceSection<Data : Any>(
        override val sectionIndex: Int,
        override var pageIndex: Int = sectionIndex,
    ) : CompositePresenterSection<Data>()
}
package ru.snowmaze.pagingflow.diff.mediums.composite

import kotlinx.coroutines.flow.StateFlow
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.internal.CompositePresenterSection

class CompositePagingChangesMediumBuilder<Key : Any, Data : Any>(
    private val pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val config: DataChangesMediumConfig = pagingDataChangesMedium.config,
) {

    private var index = 0

    companion object {

        fun <Key : Any, Data : Any> create(
            pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
            config: DataChangesMediumConfig = pagingDataChangesMedium.config,
            builder: CompositePagingChangesMediumBuilder<Key, Data>.() -> Unit,
        ) = CompositePagingChangesMediumBuilder(
            pagingDataChangesMedium = pagingDataChangesMedium,
            config = config
        ).apply(builder).build()
    }

    private val sections = mutableListOf<CompositePresenterSection<Data>>()

    fun section(itemsProvider: () -> List<Data>) {
        addSection(CompositePresenterSection.SimpleSection(itemsProvider, index))
    }

    fun flowSection(flow: StateFlow<List<Data>>) {
        addSection(CompositePresenterSection.FlowSimpleSection(flow, index))
    }

    fun dataSourceSection() {
        addSection(CompositePresenterSection.DataSourceSection(index))
    }

    private fun addSection(section: CompositePresenterSection<Data>) {
        sections.add(section)
        index++
    }

    fun build() = CompositePagingChangesMedium(
        pagingDataChangesMedium = pagingDataChangesMedium,
        sections = sections.toList(),
        config = config
    )
}
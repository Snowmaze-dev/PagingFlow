package ru.snowmaze.pagingflow.diff.mediums.composite

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMediumConfig

class CompositePagingDataChangesMediumBuilder<Key : Any, Data : Any, NewData : Any>(
    private val pagingEventsMedium: PagingEventsMedium<Key, Data>,
    private val config: PagingEventsMediumConfig = pagingEventsMedium.config
) {

    companion object {

        fun <Key : Any, Data : Any, NewData : Any> build(
            pagingEventsMedium: PagingEventsMedium<Key, Data>,
            config: PagingEventsMediumConfig = pagingEventsMedium.config,
            builder: CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.() -> Unit
        ) = CompositePagingDataChangesMediumBuilder<Key, Data, NewData>(
            pagingEventsMedium = pagingEventsMedium,
            config = config
        ).apply(builder).build()
    }

    private val sections = mutableListOf<CompositePresenterSection<Key, Data, NewData>>()

    /**
     * Adds simple section
     * @param updateWhenDataUpdated if something updated (for example received events from paging source
     * this section update will be requested
     */
    fun section(
        updateWhenDataUpdated: Boolean = false,
        itemsProvider: () -> List<NewData>
    ) {
        sections.add(CompositePresenterSection.SimpleSection(updateWhenDataUpdated, itemsProvider))
    }

    fun flowSection(itemsProvider: Flow<List<NewData>>) {
        sections.add(CompositePresenterSection.FlowSection(itemsProvider))
    }

    fun dataSourceSection(
        dataSourceIndex: Int, mapper: (List<Data>) -> List<NewData>
    ) {
        sections.add(CompositePresenterSection.DataSourceSection(dataSourceIndex, mapper))
    }

    fun build() = CompositePagingEventsMedium(
        pagingEventsMedium = pagingEventsMedium,
        sections = sections,
        config = config
    )
}

fun <Key : Any, Data : Any, NewData : Any> CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.flowSection(
    flowCollector: FlowCollector<List<NewData>>.() -> Unit
) = flowSection(flow(flowCollector))

fun <Key : Any, Data : Any, NewData : Any> CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.dataSourceSectionMapped(
    dataSourceIndex: Int
) = dataSourceSection(dataSourceIndex) { it as List<NewData> }

fun <Key : Any, Data : Any, NewData : Any> CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.section(
    items: List<NewData>
) = section(updateWhenDataUpdated = false) { items }
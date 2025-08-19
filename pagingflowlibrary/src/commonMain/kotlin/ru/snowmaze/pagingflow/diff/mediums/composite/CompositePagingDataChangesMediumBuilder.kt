package ru.snowmaze.pagingflow.diff.mediums.composite

import androidx.collection.MutableObjectList
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import ru.snowmaze.pagingflow.ExperimentalPagingApi
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMediumConfig

@ExperimentalPagingApi
class CompositePagingDataChangesMediumBuilder<Key : Any, Data : Any, NewData : Any>(
    private val pagingEventsMedium: PagingEventsMedium<Key, Data>,
    private val config: PagingEventsMediumConfig = pagingEventsMedium.config
) {

    companion object {

        @ExperimentalPagingApi
        fun <Key : Any, Data : Any, NewData : Any> build(
            pagingEventsMedium: PagingEventsMedium<Key, Data>,
            config: PagingEventsMediumConfig = pagingEventsMedium.config,
            builder: CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.() -> Unit
        ) = CompositePagingDataChangesMediumBuilder<Key, Data, NewData>(
            pagingEventsMedium = pagingEventsMedium,
            config = config
        ).apply(builder).build()
    }

    private val sections = MutableObjectList<CompositePresenterSection<Key, Data, NewData>>()

    /**
     * Adds simple section
     * @param updateWhenDataUpdated if something updated (for example received events from paging source
     * this section update will be requested
     */
    fun section(
        updateWhenDataUpdated: Boolean = false,
        itemsProvider: () -> List<NewData?>
    ) {
        sections.add(CompositePresenterSection.SimpleSection(updateWhenDataUpdated, itemsProvider))
    }

    fun flowSection(itemsProvider: Flow<List<NewData?>>) {
        sections.add(CompositePresenterSection.FlowSection(itemsProvider))
    }

    fun dataSourceSection(
        dataSourceIndex: Int, mapper: (PageChangedEvent<Key, Data>) -> List<NewData?>
    ) {
        sections.add(CompositePresenterSection.DataSourceSection(dataSourceIndex, mapper))
    }

    fun build() = CompositePagingEventsMedium(
        pagingEventsMedium = pagingEventsMedium,
        sections = sections,
        config = config
    )
}

@OptIn(ExperimentalPagingApi::class)
inline fun <Key : Any, Data : Any, NewData : Any, T> CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.mapFlowSection(
    flow: Flow<T>, crossinline mapper: (T) -> List<NewData>?
) = flowSection(callbackFlow {
    flow.collectLatest {
        val mapped = mapper(it)
        if (mapped != null) send(mapped)
    }
    awaitCancellation()
})

@OptIn(ExperimentalPagingApi::class)
inline fun <Key : Any, Data : Any, NewData : Any> CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.flowSection(
    crossinline flowCollector: suspend ProducerScope<List<NewData>>.() -> Unit
) = flowSection(callbackFlow {
    flowCollector()
    awaitCancellation()
})

@OptIn(ExperimentalPagingApi::class)
fun <Key : Any, Data : Any, NewData : Any> CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.dataSourceSectionMapped(
    dataSourceIndex: Int
) = dataSourceSection(dataSourceIndex) { it as List<NewData> }

@OptIn(ExperimentalPagingApi::class)
fun <Key : Any, Data : Any, NewData : Any> CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.section(
    items: List<NewData>
) = section(updateWhenDataUpdated = false) { items }
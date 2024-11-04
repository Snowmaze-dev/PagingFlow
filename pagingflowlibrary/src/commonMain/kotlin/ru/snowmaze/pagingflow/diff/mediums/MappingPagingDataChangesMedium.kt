package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.handle

class MappingPagingDataChangesMedium<Key : Any, Data : Any, NewData : Any>(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    override val config: DataChangesMediumConfig = pagingDataChangesMedium.config,
    private val transformOtherEvents: (
        (DataChangedEvent<Key, Data>) -> DataChangedEvent<Key, NewData>
    )? = null,
    private val transform: (PageChangedEvent<Key, Data>) -> List<NewData?>,
) : SubscribeForChangesDataChangesMedium<Key, NewData, Data>(pagingDataChangesMedium) {

    private val callback = object : DataChangedCallback<Key, Data> {

        fun handleEvent(event: DataChangedEvent<Key, Data>): DataChangedEvent<Key, NewData>? {
            return event.handle(
                onPageAdded = {
                    PageAddedEvent(
                        key = it.key,
                        sourceIndex = it.sourceIndex,
                        pageIndex = it.pageIndex,
                        pageIndexInSource = it.pageIndexInSource,
                        items = transform(it) as List<NewData>,
                        params = it.params
                    )
                },
                onPageChanged = {
                    PageChangedEvent(
                        key = it.key,
                        sourceIndex = it.sourceIndex,
                        pageIndex = it.pageIndex,
                        pageIndexInSource = it.pageIndexInSource,
                        items = transform(it),
                        params = it.params
                    )
                },
                onPageRemovedEvent = { it as PageRemovedEvent<Key, NewData> },
                onInvalidate = { it as InvalidateEvent<Key, NewData> },
                onElse = { transformOtherEvents?.invoke(it) }
            )
        }

        override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
            notifyOnEvents(
                events.mapNotNullTo(ArrayList(events.size)) { handleEvent(it) }
            )
        }

        override suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
            notifyOnEvent(handleEvent(event) ?: return)
        }
    }

    override fun getChangesCallback() = callback
}
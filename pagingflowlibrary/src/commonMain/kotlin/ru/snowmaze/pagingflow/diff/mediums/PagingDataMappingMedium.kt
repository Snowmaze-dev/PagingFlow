package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent

class PagingDataMappingMedium<Key : Any, Data : Any, NewData : Any>(
    dataChangesMedium: DataChangesMedium<Key, Data>,
    private val transform: (List<Data?>) -> List<NewData?>
) : DefaultDataChangesMedium<Key, NewData>() {

    init {
        dataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            fun handleEvent(event: DataChangedEvent<Key, Data>): DataChangedEvent<Key, NewData>? {
                return event.handle(
                    onPageAdded = {
                        PageAddedEvent(
                            key = it.key,
                            pageIndex = it.pageIndex,
                            sourceIndex = it.sourceIndex,
                            items = transform(it.items) as List<NewData>,
                        )
                    },
                    onPageChanged = {
                        PageAddedEvent(
                            key = it.key,
                            pageIndex = it.pageIndex,
                            sourceIndex = it.sourceIndex,
                            items = transform(it.items) as List<NewData>,
                        )
                    },
                    onPageRemovedEvent = { it as PageRemovedEvent<Key, NewData> },
                    onInvalidate = { it as InvalidateEvent<Key, NewData> }
                )
            }

            override fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                notifyOnEvents(events.mapNotNull { handleEvent(it) })
            }

            override fun onEvent(event: DataChangedEvent<Key, Data>) {
                notifyOnEvent(handleEvent(event) ?: return)
            }
        })
    }
}
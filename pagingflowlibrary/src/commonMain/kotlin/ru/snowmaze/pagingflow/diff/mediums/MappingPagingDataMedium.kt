package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent

class MappingPagingDataMedium<Key : Any, Data : Any, NewData : Any>(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    override val config: DataChangesMediumConfig = pagingDataChangesMedium.config,
    private val transform: (PageChangedEvent<Key, Data>) -> List<NewData?>,
) : DefaultPagingDataChangesMedium<Key, NewData>() {

    init {
        pagingDataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            fun handleEvent(event: DataChangedEvent<Key, Data>): DataChangedEvent<Key, NewData>? {

                // TODO сделать маппинг других эвентов
                return event.handle(
                    onPageAdded = {
                        PageAddedEvent(
                            key = it.key,
                            pageIndex = it.pageIndex,
                            sourceIndex = it.sourceIndex,
                            items = transform(it) as List<NewData>,
                            params = it.params
                        )
                    },
                    onPageChanged = {
                        PageChangedEvent(
                            key = it.key,
                            pageIndex = it.pageIndex,
                            sourceIndex = it.sourceIndex,
                            items = transform(it) as List<NewData>,
                            params = it.params
                        )
                    },
                    onPageRemovedEvent = { it as PageRemovedEvent<Key, NewData> },
                    onInvalidate = { it as InvalidateEvent<Key, NewData> },
                )
            }

            override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                notifyOnEvents(events.mapNotNull { handleEvent(it) })
            }

            override suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
                notifyOnEvent(handleEvent(event) ?: return)
            }
        })
    }
}
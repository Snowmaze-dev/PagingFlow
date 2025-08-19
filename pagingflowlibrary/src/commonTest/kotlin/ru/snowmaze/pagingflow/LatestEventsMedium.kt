package ru.snowmaze.pagingflow

import kotlinx.coroutines.flow.MutableStateFlow
import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.DefaultPagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium

class LatestEventsMedium<Key : Any, Data : Any>(
    pagingEventsMedium: PagingEventsMedium<Key, Data>,
    override val config: PagingEventsMediumConfig = pagingEventsMedium.config
) : DefaultPagingEventsMedium<Key, Data>() {

    var lastEvents = emptyList<PagingEvent<Key, Data>>()
    val eventsFlow = MutableStateFlow(emptyList<PagingEvent<Key, Data>>())

    init {
        pagingEventsMedium.addPagingEventsListener(
            object : PagingEventsListener<Key, Data> {
                override suspend fun onEvents(events: List<PagingEvent<Key, Data>>) {
                    lastEvents = events
                    eventsFlow.emit(events)
                }
            }
        )
    }
}
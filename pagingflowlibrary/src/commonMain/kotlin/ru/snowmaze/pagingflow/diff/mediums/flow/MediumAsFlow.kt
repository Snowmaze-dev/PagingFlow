package ru.snowmaze.pagingflow.diff.mediums.flow

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium

fun <Key : Any, Data : Any> PagingEventsMedium<Key, Data>.asFlow(
): Flow<List<PagingEvent<Key, Data>>> = callbackFlow {
    val pagingEventsListener = object : PagingEventsListener<Key, Data> {
        override suspend fun onEvents(events: List<PagingEvent<Key, Data>>) {
            send(events)
        }

        override suspend fun onEvent(event: PagingEvent<Key, Data>) {
            send(listOf(event))
        }
    }
    addPagingEventsListener(pagingEventsListener)
    awaitClose { removePagingEventsListener(pagingEventsListener) }
}

inline fun <Key : Any, Data : Any> Flow<List<PagingEvent<Key, Data>>>.asPagingEventsMedium(
    config: PagingEventsMediumConfig
): PagingEventsMedium<Key, Data> = FlowPagingEventsMedium(config, this)
package ru.snowmaze.pagingflow.diff.mediums

import androidx.collection.MutableScatterMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.PagingEvent

/**
 * Buffers events if medium have no subscribers and then replies them when previous subscribers resubscribe
 * TODO cover with tests
 */
class BufferEventsEventsMedium<Key : Any, Data : Any>(
    dataChangesMedium: PagingEventsMedium<Key, Data>,
    override val config: PagingEventsMediumConfig = dataChangesMedium.config
) : DefaultPagingEventsMedium<Key, Data>() {

    private val cachedEvents = MutableScatterMap<PagingEventsListener<*, *>, MutableList<Any>>()
    private val mutex = Mutex()

    init {
        dataChangesMedium.addPagingEventsListener(object : PagingEventsListener<Key, Data> {
            override suspend fun onEvents(events: List<PagingEvent<Key, Data>>) {
                mutex.withLock {
                    notifyOnEvents(events)
                    cachedEvents.forEachValue { value -> value.addAll(events) }
                }
            }

            override suspend fun onEvent(event: PagingEvent<Key, Data>) {
                mutex.withLock {
                    notifyOnEvent(event)
                    cachedEvents.forEachValue { value -> value.add(event) }
                }
            }
        })
    }

    fun removeCachedEvents(
        callback: PagingEventsListener<Key, Data>
    ): List<PagingEvent<Key, Data>>? {
        return cachedEvents.remove(callback)?.flatMap {
            if (it is PagingEvent<*, *>) listOf(it as PagingEvent<Key, Data>)
            else it as List<PagingEvent<Key, Data>>
        }
    }

    override fun addPagingEventsListener(listener: PagingEventsListener<Key, Data>) {
        config.coroutineScope.launch {
            mutex.withLock {
                val events = cachedEvents.remove(listener)
                if (events != null) {
                    for (obj in events) {
                        if (obj is PagingEvent<*, *>) {
                            listener.onEvent(obj as PagingEvent<Key, Data>)
                        } else {
                            listener.onEvents(obj as List<PagingEvent<Key, Data>>)
                        }
                    }
                }
                super.addPagingEventsListener(listener)
            }
        }
    }

    override fun removePagingEventsListener(listener: PagingEventsListener<Key, Data>): Boolean {
        val contains = pagingEventsListeners.contains(listener)
        config.coroutineScope.launch {
            mutex.withLock {
                cachedEvents[listener] = mutableListOf()
                super.removePagingEventsListener(listener)
            }
        }
        return contains
    }
}
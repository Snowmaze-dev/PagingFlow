package ru.snowmaze.pagingflow.diff.mediums

import androidx.collection.MutableScatterMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent

/**
 * Buffers events if medium have no subscribers and then replies them when previous subscribers resubscribe
 * TODO cover with tests
 */
class BufferEventsDataChangesMedium<Key : Any, Data : Any>(
    dataChangesMedium: PagingDataChangesMedium<Key, Data>,
    override val config: DataChangesMediumConfig = dataChangesMedium.config
) : DefaultPagingDataChangesMedium<Key, Data>() {

    private val cachedEvents = MutableScatterMap<DataChangedCallback<*, *>, MutableList<Any>>()
    private val mutex = Mutex()

    init {
        dataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {
            override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                mutex.withLock {
                    notifyOnEvents(events)
                    cachedEvents.forEachValue { value -> value.addAll(events) }
                }
            }

            override suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
                mutex.withLock {
                    notifyOnEvent(event)
                    cachedEvents.forEachValue { value -> value.add(event) }
                }
            }
        })
    }

    fun removeCachedEvents(
        callback: DataChangedCallback<Key, Data>
    ): List<DataChangedEvent<Key, Data>>? {
        return cachedEvents.remove(callback)?.flatMap {
            if (it is DataChangedEvent<*, *>) listOf(it as DataChangedEvent<Key, Data>)
            else it as List<DataChangedEvent<Key, Data>>
        }
    }

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        config.coroutineScope.launch {
            mutex.withLock {
                val events = cachedEvents.remove(callback)
                if (events != null) {
                    for (obj in events) {
                        if (obj is DataChangedEvent<*, *>) {
                            callback.onEvent(obj as DataChangedEvent<Key, Data>)
                        } else {
                            callback.onEvents(obj as List<DataChangedEvent<Key, Data>>)
                        }
                    }
                }
                super.addDataChangedCallback(callback)
            }
        }
    }

    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>): Boolean {
        val contains = dataChangedCallbacks.contains(callback)
        config.coroutineScope.launch {
            mutex.withLock {
                cachedEvents[callback] = mutableListOf()
                super.removeDataChangedCallback(callback)
            }
        }
        return contains
    }
}
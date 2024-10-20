package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent

abstract class DefaultPagingDataChangesMedium<Key : Any, Data : Any> : PagingDataChangesMedium<Key, Data> {

    protected val dataChangedCallbacks = mutableListOf<DataChangedCallback<Key, Data>>() // TODO sync

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataChangedCallbacks.add(callback)
    }

    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>): Boolean {
        return dataChangedCallbacks.remove(callback)
    }

    protected inline fun callDataChangedCallbacks(
        block: DataChangedCallback<Key, Data>.() -> Unit
    ) {
        dataChangedCallbacks.forEach(block)
    }

    protected suspend inline fun notifyOnEvent(event: DataChangedEvent<Key, Data>) {
        callDataChangedCallbacks { onEvent(event) }
    }

    protected suspend inline fun notifyOnEvents(events: List<DataChangedEvent<Key, Data>>) {
        callDataChangedCallbacks { onEvents(events) }
    }

    protected fun createDefaultDataChangedCallback(
    ) = object : DataChangedCallback<Key, Data> {

        override suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
            callDataChangedCallbacks {
                onEvent(event)
            }
        }

        override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
            callDataChangedCallbacks {
                onEvents(events)
            }
        }
    }
}
package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.utils.fastForEach
import kotlin.concurrent.Volatile

abstract class DefaultPagingDataChangesMedium<Key : Any, Data : Any> : PagingDataChangesMedium<Key, Data> {

    @Volatile
    protected var dataChangedCallbacks = listOf<DataChangedCallback<Key, Data>>()

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataChangedCallbacks += callback
    }

    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>): Boolean {
        val removed = dataChangedCallbacks.contains(callback)
        dataChangedCallbacks -= callback
        return removed
    }

    protected inline fun callDataChangedCallbacks(
        block: DataChangedCallback<Key, Data>.() -> Unit
    ): Boolean {
        val dataChangedCallbacks = dataChangedCallbacks
        dataChangedCallbacks.fastForEach(block)
        return dataChangedCallbacks.isNotEmpty()
    }

    protected suspend inline fun notifyOnEvent(
        event: DataChangedEvent<Key, Data>
    ) = callDataChangedCallbacks { onEvent(event) }

    protected suspend inline fun notifyOnEvents(
        events: List<DataChangedEvent<Key, Data>>
    ) = callDataChangedCallbacks { onEvents(events) }

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
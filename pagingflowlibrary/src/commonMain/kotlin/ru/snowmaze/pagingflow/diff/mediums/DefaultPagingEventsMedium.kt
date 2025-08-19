package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.utils.fastForEach
import kotlin.concurrent.Volatile

abstract class DefaultPagingEventsMedium<Key : Any, Data : Any> : PagingEventsMedium<Key, Data> {

    @Volatile
    protected var pagingEventsListeners = listOf<PagingEventsListener<Key, Data>>()

    override fun addPagingEventsListener(listener: PagingEventsListener<Key, Data>) {
        pagingEventsListeners += listener
    }

    override fun removePagingEventsListener(listener: PagingEventsListener<Key, Data>): Boolean {
        val removed = pagingEventsListeners.contains(listener)
        pagingEventsListeners -= listener
        return removed
    }

    protected inline fun callEventsListeners(
        block: PagingEventsListener<Key, Data>.() -> Unit
    ): Boolean {
        val pagingEventsListeners = pagingEventsListeners
        pagingEventsListeners.fastForEach(block)
        return pagingEventsListeners.isNotEmpty()
    }

    protected suspend inline fun notifyOnEvent(
        event: PagingEvent<Key, Data>
    ) = callEventsListeners { onEvent(event) }

    protected suspend inline fun notifyOnEvents(
        events: List<PagingEvent<Key, Data>>
    ) = callEventsListeners { onEvents(events) }

    protected fun createDefaultDataChangedCallback(
    ) = object : PagingEventsListener<Key, Data> {

        override suspend fun onEvent(event: PagingEvent<Key, Data>) {
            callEventsListeners {
                onEvent(event)
            }
        }

        override suspend fun onEvents(events: List<PagingEvent<Key, Data>>) {
            callEventsListeners {
                onEvents(events)
            }
        }
    }
}
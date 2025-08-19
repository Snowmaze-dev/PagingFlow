package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.PagingEventsListener

abstract class SubscribeForChangesEventsMedium<Key : Any, Data : Any, Output: Any>(
    private val pagingEventsMedium: PagingEventsMedium<Key, Data>,
) : DefaultPagingEventsMedium<Key, Output>() {

    private var lastCallback: PagingEventsListener<Key, Data>? = null

    private fun subscribeForEvents() {
        if (lastCallback != null) return
        lastCallback?.let { pagingEventsMedium.removePagingEventsListener(it) }
        val newCallback = getChangesCallback()
        pagingEventsMedium.addPagingEventsListener(newCallback)
        lastCallback = newCallback
    }

    private fun unsubscribeFromEvents() {
        lastCallback?.let { pagingEventsMedium.removePagingEventsListener(it) }
        lastCallback = null
    }

    override fun addPagingEventsListener(listener: PagingEventsListener<Key, Output>) {
        super.addPagingEventsListener(listener)
        if (pagingEventsListeners.isNotEmpty()) subscribeForEvents()
    }

    override fun removePagingEventsListener(listener: PagingEventsListener<Key, Output>): Boolean {
        val removed = super.removePagingEventsListener(listener)
        if (removed && pagingEventsListeners.isEmpty()) unsubscribeFromEvents()
        return removed
    }

    abstract fun getChangesCallback(): PagingEventsListener<Key, Data>
}
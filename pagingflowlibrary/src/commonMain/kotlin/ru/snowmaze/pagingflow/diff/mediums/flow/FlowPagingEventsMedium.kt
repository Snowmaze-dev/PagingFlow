package ru.snowmaze.pagingflow.diff.mediums.flow

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.mediums.DefaultPagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMediumConfig

class FlowPagingEventsMedium<Key : Any, Data : Any>(
    override val config: PagingEventsMediumConfig,
    private val flow: Flow<List<PagingEvent<Key, Data>>>,
) : DefaultPagingEventsMedium<Key, Data>() {

    private var subscribeJob: Job? = null

    override fun addPagingEventsListener(listener: PagingEventsListener<Key, Data>) {
        super.addPagingEventsListener(listener)
        if (pagingEventsListeners.isNotEmpty()) subscribe()
    }

    override fun removePagingEventsListener(listener: PagingEventsListener<Key, Data>): Boolean {
        val removed = super.removePagingEventsListener(listener)
        if (removed && pagingEventsListeners.isEmpty()) {
            subscribeJob?.cancel()
            subscribeJob = null
        }
        return removed
    }

    private fun subscribe() {
        if (subscribeJob != null) return
        subscribeJob = config.coroutineScope.launch {
            flow.collect { notifyOnEvents(it) }
        }
    }
}
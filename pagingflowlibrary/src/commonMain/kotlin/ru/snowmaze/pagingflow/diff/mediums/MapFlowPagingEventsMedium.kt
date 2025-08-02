package ru.snowmaze.pagingflow.diff.mediums

import androidx.collection.MutableScatterMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.handle

class MapFlowPagingEventsMedium<Key : Any, Data : Any, NewData : Any>(
    pagingEventsMedium: PagingEventsMedium<Key, Data>,
    override val config: PagingEventsMediumConfig = pagingEventsMedium.config,
    private val transformOtherEvents: (
        (PagingEvent<Key, Data>) -> EventsMapper<Key, NewData>
    )? = null,
    private val transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>,
) : SubscribeForChangesEventsMedium<Key, Data, NewData>(pagingEventsMedium) {

    private val addedJobsMap = MutableScatterMap<Int, Job>()
    private val jobsMap = MutableScatterMap<Int, Job>()
    private val otherEventsListeners = MutableScatterMap<Long, Job>()

    private val callback = object : PagingEventsListener<Key, Data> {

        // TODO unsubscribe when all subscribers unsubscribed
        suspend fun handleEvent(newEvent: PagingEvent<Key, Data>) {
            newEvent.handle(
                onPageAdded = { event ->
                    var isFirstValue = true
                    addedJobsMap.remove(event.pageIndex)?.cancelAndJoin()
                    addedJobsMap.put(event.pageIndex, config.coroutineScope.launch {
                        transform(event).collect {
                            notifyOnEvent(
                                if (isFirstValue) {
                                    PageAddedEvent(
                                        key = event.key,
                                        sourceIndex = event.sourceIndex,
                                        pageIndex = event.pageIndex,
                                        pageIndexInSource = event.pageIndexInSource,
                                        items = it as List<NewData>,
                                        params = event.params,
                                        changeType = event.changeType
                                    )
                                } else {
                                    PageChangedEvent(
                                        key = event.key,
                                        sourceIndex = event.sourceIndex,
                                        pageIndex = event.pageIndex,
                                        pageIndexInSource = event.pageIndexInSource,
                                        items = it as List<NewData>,
                                        params = event.params,
                                        previousItemCount = event.previousItemCount,
                                        changeType = event.changeType
                                    )
                                }
                            )
                            isFirstValue = false
                        }
                    })
                },
                onPageChanged = { event ->
                    addedJobsMap.remove(event.pageIndex)?.cancelAndJoin()
                    jobsMap.remove(event.pageIndex)?.cancelAndJoin()
                    jobsMap.put(event.pageIndex, config.coroutineScope.launch {
                        transform(event).collect {
                            notifyOnEvent(
                                PageChangedEvent(
                                    key = event.key,
                                    sourceIndex = event.sourceIndex,
                                    pageIndex = event.pageIndex,
                                    pageIndexInSource = event.pageIndexInSource,
                                    items = it as List<NewData>,
                                    params = event.params,
                                    previousItemCount = event.previousItemCount,
                                    changeType = event.changeType
                                )
                            )
                        }
                    })
                },
                onPageRemovedEvent = { it as PageRemovedEvent<Key, NewData> },
                onInvalidate = { it as InvalidateEvent<Key, NewData> },
                onElse = {
                    val transformOtherEvents = transformOtherEvents
                    if (transformOtherEvents != null) {
                        val eventsMapper = transformOtherEvents(it)
                        otherEventsListeners.remove(eventsMapper.listenerId)?.cancelAndJoin()
                        otherEventsListeners[eventsMapper.listenerId] = config.coroutineScope.launch {
                            eventsMapper.eventFlow.collect { mapped ->
                                notifyOnEvent(mapped)
                            }
                        }
                    }
                }
            )
        }

        override suspend fun onEvents(events: List<PagingEvent<Key, Data>>) {
            events.forEach { handleEvent(it) }
        }

        override suspend fun onEvent(event: PagingEvent<Key, Data>) {
            handleEvent(event)
        }
    }

    override fun getChangesCallback() = callback
}

class EventsMapper<Key : Any, Data : Any>(
    val eventFlow: Flow<PagingEvent<Key, Data>>,
    val listenerId: Long
)
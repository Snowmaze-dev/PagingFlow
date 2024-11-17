package ru.snowmaze.pagingflow.diff.mediums

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.handle
import ru.snowmaze.pagingflow.utils.platformMapOf

class MappingFlowPagingDataChangesMedium<Key : Any, Data : Any, NewData : Any>(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    override val config: DataChangesMediumConfig = pagingDataChangesMedium.config,
    private val transformOtherEvents: (
        (DataChangedEvent<Key, Data>) -> EventsMapper<Key, NewData>
    )? = null,
    private val transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>,
) : SubscribeForChangesDataChangesMedium<Key, Data, NewData>(pagingDataChangesMedium) {

    private val addedJobsMap = platformMapOf<Int, Job>()
    private val jobsMap = platformMapOf<Int, Job>()
    private val otherEventsListeners = platformMapOf<Long, Job>()

    private val callback = object : DataChangedCallback<Key, Data> {

        // TODO unsubscribe when all subscribers unsubscribed
        suspend fun handleEvent(newEvent: DataChangedEvent<Key, Data>) {
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
                                    )
                                } else {
                                    PageChangedEvent(
                                        key = event.key,
                                        sourceIndex = event.sourceIndex,
                                        pageIndex = event.pageIndex,
                                        pageIndexInSource = event.pageIndexInSource,
                                        items = it as List<NewData>,
                                        params = event.params
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
                                    params = event.params
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

        override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
            events.forEach { handleEvent(it) }
        }

        override suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
            handleEvent(event)
        }
    }

    override fun getChangesCallback() = callback
}

class EventsMapper<Key : Any, Data : Any>(
    val eventFlow: Flow<DataChangedEvent<Key, Data>>,
    val listenerId: Long
)
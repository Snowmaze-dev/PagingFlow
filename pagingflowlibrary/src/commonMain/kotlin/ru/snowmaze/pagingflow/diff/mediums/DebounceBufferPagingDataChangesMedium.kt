package ru.snowmaze.pagingflow.diff.mediums

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent

class DebounceBufferPagingDataChangesMedium<Key : Any, Data : Any>(
    private val pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val debounceBufferDurationMsProvider: () -> Long,
    private val shouldThrottleAddPagesEvents: Boolean = false,
    override val config: DataChangesMediumConfig = pagingDataChangesMedium.config,
) : SubscribeForChangesDataChangesMedium<Key, Data, Data>(pagingDataChangesMedium) {

    private val savedEvents = mutableListOf<DataChangedEvent<Key, Data>>()
    private var job: Job? = null
    private val coroutineScope = config.coroutineScope
    private val mutex = Mutex()
    private val callback = object : DataChangedCallback<Key, Data> {

        override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
            mutex.withLock {
                var newEvents = events
                if (!shouldThrottleAddPagesEvents && debounceBufferDurationMsProvider() != 0L) {
                    val lastIndex = events.indexOfLast { it is PageAddedEvent<Key, Data> }
                    if (lastIndex != -1) {
                        val toIndex = lastIndex + 1
                        val notifyEvents = savedEvents + events.subList(0, toIndex)
                        savedEvents.clear()
                        notifyOnEvents(notifyEvents)
                        newEvents = if (toIndex == events.size) emptyList()
                        else events.subList(toIndex, events.size)
                    }
                }
                if (newEvents.isNotEmpty()) {
                    savedEvents.addAll(newEvents)
                    sendEvents()
                }
            }
        }

        override suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
            mutex.withLock {
                if (event is PageAddedEvent && !shouldThrottleAddPagesEvents) {
                    if (savedEvents.isEmpty()) notifyOnEvent(event)
                    else {
                        val newList = savedEvents + event
                        savedEvents.clear()
                        notifyOnEvents(newList)
                    }
                } else {
                    savedEvents.add(event)
                    sendEvents()
                }
            }
        }
    }

    override fun getChangesCallback() = callback

    fun sendEvents() {
        job?.cancel()
        job = coroutineScope.launch(config.processingDispatcher) {
            delay(debounceBufferDurationMsProvider())
            mutex.withLock {
                notifyOnEvents(savedEvents.toList())
                savedEvents.clear()
            }
        }
    }
}
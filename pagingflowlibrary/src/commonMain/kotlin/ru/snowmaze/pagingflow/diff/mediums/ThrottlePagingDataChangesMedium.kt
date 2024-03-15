package ru.snowmaze.pagingflow.diff.mediums

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat

class ThrottlePagingDataChangesMedium<Key : Any, Data : Any>(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val throttleDurationMsProvider: () -> Long,
    private val shouldThrottleAddPagesEvents: Boolean = false,
    override val config: DataChangesMediumConfig = pagingDataChangesMedium.config,
) : DefaultPagingDataChangesMedium<Key, Data>() {

    private val savedEvents = mutableListOf<DataChangedEvent<Key, Data>>()
    private var job: Job? = null
    private val coroutineScope = config.coroutineScope
    protected val processingDispatcher = config.processingDispatcher.limitedParallelismCompat(1)

    init {
        pagingDataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                withContext(processingDispatcher) {
                    var newEvents = events
                    if (!shouldThrottleAddPagesEvents && throttleDurationMsProvider() != 0L) {
                        for ((index, event) in events.withIndex()) {
                            if (event is PageAddedEvent) {
                                val toIndex = index + 1
                                val notifyOnEventsArr = savedEvents + events.subList(0, toIndex)
                                notifyOnEvents(notifyOnEventsArr)

                                savedEvents.clear()
                                if (toIndex == events.size) {
                                    newEvents = emptyList()
                                    break
                                }
                                newEvents = events.subList(toIndex, events.size)
                                break
                            }
                        }
                    }
                    if (newEvents.isNotEmpty()) {
                        savedEvents.addAll(newEvents)
                        sendEvents()
                    }
                }
            }

            override suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
                withContext(processingDispatcher) {
                    if (event is PageAddedEvent && !shouldThrottleAddPagesEvents) {
                        notifyOnEvent(event)
                    } else {
                        savedEvents.add(event)
                        sendEvents()
                    }
                }
            }
        })
    }

    fun sendEvents() {
        job?.cancel()
        job = coroutineScope.launch(processingDispatcher) {
            delay(throttleDurationMsProvider())
            notifyOnEvents(savedEvents.toList())
            savedEvents.clear()
        }
    }
}
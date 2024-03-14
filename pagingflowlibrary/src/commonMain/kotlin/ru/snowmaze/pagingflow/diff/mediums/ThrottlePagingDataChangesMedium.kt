package ru.snowmaze.pagingflow.diff.mediums

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat

class ThrottlePagingDataChangesMedium<Key : Any, Data : Any>(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val throttleDurationMsProvider: () -> Long,
    private val shouldSendAddEventWithoutDelay: Boolean = false,
    override val config: DataChangesMediumConfig = pagingDataChangesMedium.config,
) : DefaultPagingDataChangesMedium<Key, Data>() {

    private val savedEvents = mutableListOf<DataChangedEvent<Key, Data>>()
    private var job: Job? = null
    private val coroutineScope = config.coroutineScope
    protected val processingDispatcher = config.processingDispatcher.limitedParallelismCompat(1)

    init {
        pagingDataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            override fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                coroutineScope.launch(processingDispatcher) {
                    var isHandled = false
                    if (shouldSendAddEventWithoutDelay && throttleDurationMsProvider() != 0L) {
                        for ((index, event) in events.withIndex()) {
                            if (event is PageAddedEvent) {
                                val notifyOnEventsArr = events.subList(0, index + 1)
                                notifyOnEvents(notifyOnEventsArr)
                                savedEvents.addAll(events.subList(index + 1, events.size))
                                isHandled = true
                                coroutineScope.launch(processingDispatcher) {
                                    delay(throttleDurationMsProvider())
                                    notifyOnEvents(savedEvents.toList())
                                    savedEvents.clear()
                                }
                                break
                            }
                        }
                    }
                    if (!isHandled) {
                        savedEvents.addAll(events)
                        sendEvents()
                    }
                }
            }

            override fun onEvent(event: DataChangedEvent<Key, Data>) {
                coroutineScope.launch(processingDispatcher) {
                    if (event is PageAddedEvent && shouldSendAddEventWithoutDelay) {
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
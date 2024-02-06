package ru.snowmaze.pagingflow.diff.mediums

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat

class ThrottleDataChangesMedium<Key : Any, Data : Any>(
    dataChangesMedium: DataChangesMedium<Key, Data>,
    private val throttleDurationMs: Long,
    private val coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher
) : DefaultDataChangesMedium<Key, Data>() {

    private val savedEvents = mutableListOf<DataChangedEvent<Key, Data>>()
    private var job: Job? = null
    protected val processingDispatcher = processingDispatcher.limitedParallelismCompat(1)

    init {
        dataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            override fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                coroutineScope.launch(processingDispatcher) {
                    savedEvents.addAll(events)
                    sendEvents()
                }
            }

            override fun onEvent(event: DataChangedEvent<Key, Data>) {
                coroutineScope.launch(processingDispatcher) {
                    savedEvents.add(event)
                    sendEvents()
                }
            }
        })
    }

    fun sendEvents() {
        job?.cancel()
        job = coroutineScope.launch(processingDispatcher) {
            delay(throttleDurationMs)
            notifyOnEvents(savedEvents.toList())
            savedEvents.clear()
        }
    }
}
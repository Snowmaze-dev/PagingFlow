package ru.snowmaze.pagingflow.diff.mediums

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.utils.fastIndexOfLast

class BatchingPagingDataChangesMedium<Key : Any, Data : Any>(
    private val pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val eventsBatchingDurationMsProvider: () -> Long,
    private val shouldBatchAddPagesEvents: Boolean = false,
    override val config: DataChangesMediumConfig = pagingDataChangesMedium.config,
) : SubscribeForChangesDataChangesMedium<Key, Data, Data>(pagingDataChangesMedium),
    DataChangedCallback<Key, Data> {

    private val savedEvents = mutableListOf<DataChangedEvent<Key, Data>>()
    private var job: Job? = null
    private val coroutineScope = config.coroutineScope
    private val mutex = Mutex()

    override fun getChangesCallback() = this

    override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
        mutex.withLock {
            var newEvents = events
            val batchingTime = eventsBatchingDurationMsProvider()
            if (!shouldBatchAddPagesEvents && batchingTime != 0L) {
                val lastIndex = events.fastIndexOfLast { it is PageAddedEvent<Key, Data> }
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
                if (batchingTime == 0L) notifyOnEventsInternal()
                else sendEvents()
            }
        }
    }

    override suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
        mutex.withLock {
            val batchingTime = eventsBatchingDurationMsProvider()
            if (event is PageAddedEvent && !shouldBatchAddPagesEvents && batchingTime != 0L) {
                if (savedEvents.isEmpty()) notifyOnEvent(event)
                else {
                    val newList = savedEvents + event
                    savedEvents.clear()
                    notifyOnEvents(newList)
                }
            } else {
                savedEvents.add(event)
                if (batchingTime == 0L) {
                    job?.cancel()
                    notifyOnEventsInternal()
                } else sendEvents(batchingTime)
            }
        }
    }

    fun sendEvents(batchingTime: Long = eventsBatchingDurationMsProvider()): Job {
        job?.cancel()
        val job = coroutineScope.launch(config.processingDispatcher) {
            delay(batchingTime)
            mutex.withLock { notifyOnEventsInternal() }
        }
        this.job = job
        return job
    }

    private suspend inline fun notifyOnEventsInternal() {
        if (savedEvents.size == 1) notifyOnEvent(savedEvents.first())
        else notifyOnEvents(savedEvents.toList())
        savedEvents.clear()
    }
}
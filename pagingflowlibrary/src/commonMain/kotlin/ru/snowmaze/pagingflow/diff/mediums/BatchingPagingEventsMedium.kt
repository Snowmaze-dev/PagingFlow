package ru.snowmaze.pagingflow.diff.mediums

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.PagingEventsListener

class BatchingPagingEventsMedium<Key : Any, Data : Any>(
    private val pagingEventsMedium: PagingEventsMedium<Key, Data>,
    private val shouldBatch: ((PagingEvent<Key, Data>) -> Boolean)?,
    private val eventsBatchingDurationMsProvider: (List<PagingEvent<Key, Data>>) -> Long,
    override val config: PagingEventsMediumConfig = pagingEventsMedium.config,
) : SubscribeForChangesEventsMedium<Key, Data, Data>(pagingEventsMedium),
    PagingEventsListener<Key, Data> {

    private val savedEvents = mutableListOf<PagingEvent<Key, Data>>()
    private var job: Job? = null
    private val coroutineScope = config.coroutineScope
    private val mutex = Mutex()

    override fun getChangesCallback() = this

    override suspend fun onEvents(
        events: List<PagingEvent<Key, Data>>
    ): Unit = mutex.withLock {
        var newEvents = events
        var batchingTime: Long? = null
        if (shouldBatch != null) {
            val lastIndex = events.indexOfLast { !shouldBatch(it) }
            if (lastIndex == events.lastIndex) batchingTime = 0
            else if (lastIndex != -1) {
                val toIndex = lastIndex + 1
                val notifyEvents = savedEvents + events.subList(0, toIndex)
                savedEvents.clear()
                notifyOnEvents(notifyEvents)
                newEvents = if (toIndex == events.size) emptyList()
                else events.subList(toIndex, events.size)
            }
        }
        if (newEvents.isNotEmpty()) {
            if (batchingTime == null) batchingTime = eventsBatchingDurationMsProvider(events)
            savedEvents.addAll(newEvents)
            if (batchingTime == 0L) {
                job?.cancel()
                notifyOnEventsInternal()
            } else sendEvents()
        }
    }

    override suspend fun onEvent(
        event: PagingEvent<Key, Data>
    ): Unit = mutex.withLock {
        if (shouldBatch != null) {
            val shouldBatch = shouldBatch(event)
            if (!shouldBatch) {
                if (savedEvents.isEmpty()) notifyOnEvent(event)
                else {
                    val newList = savedEvents + event
                    savedEvents.clear()
                    notifyOnEvents(newList)
                }
                return@withLock
            }
        }
        savedEvents.add(event)
        val batchingTime = eventsBatchingDurationMsProvider(savedEvents)
        if (batchingTime == 0L) {
            job?.cancel()
            notifyOnEventsInternal()
        } else sendEvents(batchingTime)
    }

    fun sendEvents(batchingTime: Long = eventsBatchingDurationMsProvider(savedEvents)): Job {
        job?.cancel()
        val job = coroutineScope.launch(config.processingContext) {
            delay(batchingTime)
            mutex.withLock { notifyOnEventsInternal() }
        }
        this.job = job
        return job
    }

    private suspend inline fun notifyOnEventsInternal() {
        when (savedEvents.size) {
            0 -> return
            1 -> notifyOnEvent(savedEvents.first())
            else -> notifyOnEvents(savedEvents.toList())
        }
        savedEvents.clear()
    }
}
package ru.snowmaze.pagingflow.diff.mediums

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent

class BatchingPagingDataChangesMedium<Key : Any, Data : Any>( // TODO ломает testAsync тест
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
            if (!shouldBatchAddPagesEvents && eventsBatchingDurationMsProvider() != 0L) {
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
            if (event is PageAddedEvent && !shouldBatchAddPagesEvents) {
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

    fun sendEvents() {
        job?.cancel()
        job = coroutineScope.launch(config.processingDispatcher) {
            delay(eventsBatchingDurationMsProvider())
            mutex.withLock {
                if (savedEvents.size == 1) notifyOnEvent(savedEvents.first())
                else notifyOnEvents(savedEvents.toList())
                savedEvents.clear()
            }
        }
    }
}
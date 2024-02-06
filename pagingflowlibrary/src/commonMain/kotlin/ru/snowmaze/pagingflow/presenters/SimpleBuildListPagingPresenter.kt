package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.handle

open class SimpleBuildListPagingPresenter<Key : Any, Data : Any>(
    dataChangesMedium: DataChangesMedium<Key, Data>,
    invalidateBehavior: InvalidateBehavior,
    coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher
) : BuildListPagingPresenter<Key, Data>(invalidateBehavior, coroutineScope, processingDispatcher) {

    protected val pageIndexes = mutableMapOf<Int, List<Data?>>()
    protected var pageIndexesKeys = emptyList<Int>()
    protected var changeDataJob: Job = Job()

    init {
        dataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            fun MutableMap<Int, List<Data?>>.applyEvent(event: DataChangedEvent<Key, Data>) {
                event.handle(
                    onPageAdded = { this[it.pageIndex] = it.items },
                    onPageChanged = { this[it.pageIndex] = it.items },
                    onPageRemovedEvent = { remove(it.pageIndex) },
                    onInvalidate = { onInvalidateInternal() }
                )
            }

            override fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                updateData {
                    var lastEvent: DataChangedEvent<Key, Data>? = null
                    for (event in events) {
                        applyEvent(event)
                        lastEvent = event
                    }
                    lastEvent !is InvalidateEvent<*, *>
                }
            }

            override fun onEvent(event: DataChangedEvent<Key, Data>) {
                updateData {
                    applyEvent(event)
                    event !is InvalidateEvent<*, *>
                }
            }
        })
    }

    override fun onInvalidateAdditionalAction() {
        pageIndexes.clear()
        pageIndexesKeys = emptyList()
        changeDataJob.cancel()
        changeDataJob = Job()
    }

    protected open fun updateData(update: MutableMap<Int, List<Data?>>.() -> Boolean) {
        coroutineScope.launch(processingDispatcher + changeDataJob) {
            val result = pageIndexes.update()
            if (result) {
                pageIndexesKeys = pageIndexes.keys.sorted()
                buildList()
            }
        }
    }

    /**
     * Rebuilds list
     */
    fun forceRebuildList() {
        coroutineScope.launch(processingDispatcher) {
            buildList()
        }
    }

    override fun buildListInternal(): List<Data?> {
        return buildList(pageIndexesKeys.sumOf { pageIndexes.getValue(it).size }) {
            for (pageIndex in pageIndexesKeys) {
                addAll(pageIndexes.getValue(pageIndex))
            }
        }
    }
}
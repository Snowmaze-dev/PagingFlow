package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.handle

open class SimpleBuildListPagingPresenter<Key : Any, Data : Any>(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    invalidateBehavior: InvalidateBehavior,
    config: DataChangesMediumConfig = pagingDataChangesMedium.config
) : BuildListPagingPresenter<Key, Data>(
    invalidateBehavior,
    config.coroutineScope,
    config.processingDispatcher
) {

    protected val pageIndexes = mutableMapOf<Int, List<Data?>>()
    protected var pageIndexesKeys = emptyList<Int>()
    protected var changeDataJob: Job = Job()

    init {
        pagingDataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

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
                    for (event in events) { applyEvent(event) }
                    events
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

    protected open fun updateData(update: MutableMap<Int, List<Data?>>.() -> List<DataChangedEvent<Key, Data>>) {
        coroutineScope.launch(processingDispatcher + changeDataJob) {
            val result = pageIndexes.update()
            val lastEvent = result.lastOrNull()
            if (lastEvent != null && lastEvent !is InvalidateEvent) {
                pageIndexesKeys = pageIndexes.keys.sorted()
                buildList(result)
            }
        }
    }

    /**
     * Rebuilds list
     */
    fun forceRebuildList() {
        coroutineScope.launch(processingDispatcher) {
            buildList(emptyList())
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
package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.AwaitDataSetEvent
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
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

    protected val pageIndexes = mutableMapOf<Int, PageChangedEvent<Key, Data>>()
    protected var pageIndexesKeys = emptyList<Int>()
    protected var changeDataJob: Job = Job()

    init {
        pagingDataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            fun MutableMap<Int, PageChangedEvent<Key, Data>>.applyEvent(
                event: DataChangedEvent<Key, Data>
            ) {
                event.handle(
                    onPageAdded = { this[it.pageIndex] = it },
                    onPageChanged = { this[it.pageIndex] = it },
                    onPageRemovedEvent = { remove(it.pageIndex) },
                    onInvalidate = { onInvalidateInternal() }
                )
            }

            override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                updateData {
                    for (event in events) {
                        applyEvent(event)
                    }
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

    protected open suspend fun updateData(
        update: suspend MutableMap<Int, PageChangedEvent<Key, Data>>.() -> List<DataChangedEvent<Key, Data>>
    ) {
        coroutineScope.launch(processingDispatcher + changeDataJob) {
            val result = pageIndexes.update()
            val lastEvent = result.lastOrNull()
            if (lastEvent != null && lastEvent !is InvalidateEvent) {
                pageIndexesKeys = pageIndexes.keys.sorted()
                buildList(result)
            }
            for (event in result) {
                if (event is AwaitDataSetEvent) event.callback()
            }
        }.join()
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
        return buildList(pageIndexesKeys.sumOf { pageIndexes.getValue(it).items.size }) {
            var newStartIndex = 0
            for (pageIndex in pageIndexesKeys) {
                val page = pageIndexes.getValue(pageIndex)
                if (page.changeType == PageChangedEvent.ChangeType.CHANGE_TO_NULLS) {
                    newStartIndex += page.items.size
                }
                addAll(page.items)
            }
            _startIndex = newStartIndex
        }
    }
}
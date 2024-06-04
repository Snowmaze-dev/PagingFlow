package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.AwaitDataSetEvent
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.handle

/**
 * Basic implementation of list building presenter.
 * It collects events and sets it to map of pages which will be later used to build list in [buildListInternal] implementation
 */
open class SimpleBuildListPagingPresenter<Key : Any, Data : Any>(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    invalidateBehavior: InvalidateBehavior,
    config: DataChangesMediumConfig = pagingDataChangesMedium.config,
    presenterFlow: () -> MutableSharedFlow<List<Data?>> = defaultPresenterFlowCreator()
) : BuildListPagingPresenter<Key, Data>(
    invalidateBehavior = invalidateBehavior,
    coroutineScope = config.coroutineScope,
    processingDispatcher = config.processingDispatcher,
    presenterFlow = presenterFlow,
) {

    protected val pageIndexes = mutableMapOf<Int, PageChangedEvent<Key, Data>>()
    protected var pageIndexesKeys = emptyList<Int>()

    init {
        val callback = getDataChangedCallback()
        pagingDataChangesMedium.addDataChangedCallback(callback)

        var firstCall = true
        var isSubscribedAlready = true
        coroutineScope.launch {
            _dataFlow.subscriptionCount.collectLatest { subscriptionCount ->
                if (subscriptionCount == 0 && !firstCall) {
                    isSubscribedAlready = false
                    pagingDataChangesMedium.removeDataChangedCallback(callback)
                } else if (subscriptionCount == 1 && !isSubscribedAlready) {
                    isSubscribedAlready = true
                    pagingDataChangesMedium.addDataChangedCallback(callback)
                }
                firstCall = false
            }
        }
    }

    protected open fun getDataChangedCallback() = object : DataChangedCallback<Key, Data> {

        private suspend inline fun MutableMap<Int, PageChangedEvent<Key, Data>>.applyEvent(
            event: DataChangedEvent<Key, Data>
        ) {
            event.handle(
                onPageAdded = { this[it.pageIndex] = it },
                onPageChanged = { this[it.pageIndex] = it },
                onPageRemovedEvent = { remove(it.pageIndex) },
                onInvalidate = {
                    onInvalidateInternal(
                        specifiedInvalidateBehavior = it.invalidateBehavior,
                    )
                }
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
    }

    override fun onInvalidateAdditionalAction() {
        pageIndexes.clear()
        pageIndexesKeys = emptyList()
    }

    protected open suspend fun updateData(
        update: suspend MutableMap<Int, PageChangedEvent<Key, Data>>.() -> List<DataChangedEvent<Key, Data>>
    ) {
        coroutineScope.launch(processingDispatcher) {
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

    override suspend fun buildListInternal(): List<Data?> {
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
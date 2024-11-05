package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.diff.AwaitDataSetEvent
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.handle
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium

/**
 * Basic implementation of list building presenter.
 * It collects events and sets it to map of pages which will be later used to build list in [buildListInternal] implementation
 */
open class SimpleBuildListPagingPresenter<Key : Any, Data : Any>(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    invalidateBehavior: InvalidateBehavior,
    config: DataChangesMediumConfig = pagingDataChangesMedium.config,
    unsubscribeDelayWhenNoSubscribers: Long = 5000L,
    presenterFlow: () -> MutableSharedFlow<LatestData<Data>> = defaultPresenterFlowCreator()
) : BuildListPagingPresenter<Key, Data>(
    invalidateBehavior = invalidateBehavior,
    coroutineScope = config.coroutineScope,
    processingDispatcher = config.processingDispatcher,
    presenterFlow = presenterFlow,
) {

    protected val indexedPages = mutableMapOf<Int, PageChangedEvent<Key, Data>>()

    init {
        coroutineScope.launch(processingDispatcher) {
            val callback = getDataChangedCallback()
            pagingDataChangesMedium.addDataChangedCallback(callback)

            var firstCall = true
            var isSubscribedAlready = true
            _dataFlow.subscriptionCount.collect { subscriptionCount ->
                if (subscriptionCount == 0 && !firstCall) {
                    delay(unsubscribeDelayWhenNoSubscribers)
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
        indexedPages.clear()
    }

    protected open suspend fun updateData(
        update: suspend MutableMap<Int, PageChangedEvent<Key, Data>>.() -> List<DataChangedEvent<Key, Data>>
    ): Unit = withContext(processingDispatcher) {
        val result = indexedPages.update()
        try {
            val lastEvent = result.lastOrNull()
            if (lastEvent != null && lastEvent !is InvalidateEvent) buildList(result)
        } finally {
            for (event in result) {
                if (event is AwaitDataSetEvent) event.callback()
            }
        }
    }

    /**
     * Flattens [indexedPages] Map to list
     */
    override suspend fun buildListInternal(): List<Data?> {
        val pageIndexesKeys = indexedPages.keys.sorted()
        return buildList(pageIndexesKeys.sumOf { indexedPages.getValue(it).items.size }) {
            var newStartIndex = 0
            for (pageIndex in pageIndexesKeys) {
                val page = indexedPages.getValue(pageIndex)
                if (page.changeType == PageChangedEvent.ChangeType.CHANGE_TO_NULLS) {
                    newStartIndex += page.items.size
                }
                addAll(page.items)
            }
            _startIndex = newStartIndex
        }
    }
}
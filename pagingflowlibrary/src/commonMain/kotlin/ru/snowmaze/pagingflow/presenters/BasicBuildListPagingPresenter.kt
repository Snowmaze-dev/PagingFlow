package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.utils.SingleElementList

/**
 * Basic implementation of list building presenter.
 * It collects events and sets it to map of pages which will be later used to build list in [BuildListPagingPresenter.buildList]
 */
open class BasicBuildListPagingPresenter<Key : Any, Data : Any>(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val presenterConfiguration: BasicPresenterConfiguration<Key, Data>,
    val config: DataChangesMediumConfig = pagingDataChangesMedium.config
) : BuildListPagingPresenter<Key, Data>(
    listBuildStrategy = presenterConfiguration.listBuildStrategy,
    invalidateBehavior = presenterConfiguration.invalidateBehavior,
    coroutineScope = config.coroutineScope,
    processingDispatcher = config.processingDispatcher,
    presenterFlow = presenterConfiguration.presenterFlow,
), DataChangedCallback<Key, Data> {

    private val singletonElementList = SingleElementList<DataChangedEvent<Key, Data>>()

    init {
        val callback = this
        pagingDataChangesMedium.addDataChangedCallback(callback)
        val shouldBeAlwaysSubscribed = presenterConfiguration.shouldBeAlwaysSubscribed
        if (!shouldBeAlwaysSubscribed) {
            var firstCall = true
            var isSubscribedAlready = true
            coroutineScope.launch(processingDispatcher) {
                _dataFlow.subscriptionCount.collect { subscriptionCount ->
                    if (subscriptionCount == 0 && !firstCall) {
                        delay(presenterConfiguration.unsubscribeDelayWhenNoSubscribers)
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
    }

    override suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
        withContext(processingDispatcher) {
            singletonElementList.element = event
            buildList(singletonElementList)
            singletonElementList.element = null
        }
    }

    override suspend fun onEvents(
        events: List<DataChangedEvent<Key, Data>>
    ) {
        if (events.isEmpty()) return
        withContext(processingDispatcher) { buildList(events) }
    }
}
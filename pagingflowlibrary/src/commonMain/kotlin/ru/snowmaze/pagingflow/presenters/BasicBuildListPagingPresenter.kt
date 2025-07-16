package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.utils.SingleElementList

/**
 * Basic implementation of list building presenter.
 * It collects events and sets it to map of pages which will be later used to build list in [BuildListPagingPresenter.buildList]
 */
open class BasicBuildListPagingPresenter<Key : Any, Data : Any>(
    pagingEventsMedium: PagingEventsMedium<Key, Data>,
    private val presenterConfiguration: BasicPresenterConfiguration<Key, Data>,
    val config: PagingEventsMediumConfig = pagingEventsMedium.config
) : BuildListPagingPresenter<Key, Data>(
    listBuildStrategy = presenterConfiguration.listBuildStrategy,
    invalidateBehavior = presenterConfiguration.invalidateBehavior,
    coroutineScope = config.coroutineScope,
    processingContext = config.processingContext,
    presenterFlow = presenterConfiguration.presenterFlow,
), PagingEventsListener<Key, Data> {

    private val singletonElementList = SingleElementList<PagingEvent<Key, Data>>()

    init {
        val callback = this
        pagingEventsMedium.addPagingEventsListener(callback)
        val shouldBeAlwaysSubscribed = presenterConfiguration.shouldBeAlwaysSubscribed
        if (!shouldBeAlwaysSubscribed) {
            var firstCall = true
            var isSubscribedAlready = true
            coroutineScope.launch(processingContext) {
                _dataFlow.subscriptionCount.collect { subscriptionCount ->
                    if (subscriptionCount == 0 && !firstCall) {
                        delay(presenterConfiguration.unsubscribeDelayWhenNoSubscribers)
                        isSubscribedAlready = false
                        pagingEventsMedium.removePagingEventsListener(callback)
                    } else if (subscriptionCount == 1 && !isSubscribedAlready) {
                        isSubscribedAlready = true
                        pagingEventsMedium.addPagingEventsListener(callback)
                    }
                    firstCall = false
                }
            }
        }
    }

    override suspend fun onEvent(event: PagingEvent<Key, Data>) {
        withContext(processingContext) {
            singletonElementList.element = event
            buildList(singletonElementList)
            singletonElementList.element = null
        }
    }

    override suspend fun onEvents(
        events: List<PagingEvent<Key, Data>>
    ) {
        if (events.isEmpty()) return
        withContext(processingContext) { buildList(events) }
    }
}
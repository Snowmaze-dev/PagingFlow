package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium

/**
 * Basic implementation of list building presenter.
 * It collects events and sets it to map of pages which will be later used to build list in [buildListInternal] implementation
 */
open class SimpleBuildListPagingPresenter<Key : Any, Data : Any>(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val presenterConfiguration: SimplePresenterConfiguration<Key, Data>,
    config: DataChangesMediumConfig = pagingDataChangesMedium.config,
    presenterFlow: () -> MutableSharedFlow<LatestData<Data>> = defaultPresenterFlowCreator()
) : BuildListPagingPresenter<Key, Data>(
    listBuildStrategy = presenterConfiguration.listBuildStrategy,
    invalidateBehavior = presenterConfiguration.invalidateBehavior,
    coroutineScope = config.coroutineScope,
    processingDispatcher = config.processingDispatcher,
    presenterFlow = presenterFlow,
) {

    init {
        val callback = getDataChangedCallback()
        pagingDataChangesMedium.addDataChangedCallback(callback)
        var firstCall = true
        var isSubscribedAlready = true
        val shouldSubscribeNow = presenterConfiguration.shouldSubscribeForChangesNow
        if (shouldSubscribeNow) {
            firstCall = false
            isSubscribedAlready = true
            pagingDataChangesMedium.addDataChangedCallback(callback)
        }
        if (!shouldSubscribeNow) coroutineScope.launch(processingDispatcher) {
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

    protected open fun getDataChangedCallback() = object : DataChangedCallback<Key, Data> {

        override suspend fun onEvents(
            events: List<DataChangedEvent<Key, Data>>
        ) = withContext(processingDispatcher) { buildList(events) }
    }

    override fun onInvalidateAdditionalAction() {
        presenterConfiguration.listBuildStrategy.invalidate()
    }
}
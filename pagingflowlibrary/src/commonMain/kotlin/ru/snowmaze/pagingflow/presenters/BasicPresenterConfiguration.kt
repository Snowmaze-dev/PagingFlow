package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.MutableSharedFlow
import ru.snowmaze.pagingflow.presenters.list.ListBuildStrategy
import ru.snowmaze.pagingflow.presenters.list.ListByPagesBuildStrategy

/**
 * Configuration for [BasicBuildListPagingPresenter]
 */
class BasicPresenterConfiguration<Key : Any, Data : Any>(
    val listBuildStrategy: ListBuildStrategy<Key, Data> = ListByPagesBuildStrategy(),
    val invalidateBehavior: InvalidateBehavior = InvalidateBehavior.WAIT_FOR_NEW_LIST,
    val shouldBeAlwaysSubscribed: Boolean = false,
    val unsubscribeDelayWhenNoSubscribers: Long = 5000L,
    val presenterFlow: () -> MutableSharedFlow<LatestData<Data>> = defaultPresenterFlowCreator()
)
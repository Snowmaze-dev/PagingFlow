package ru.snowmaze.pagingflow.diff

import ru.snowmaze.pagingflow.presenters.BasicBuildListPagingPresenter

/**
 * This listener called when data is changed.
 * Default presenters like [BasicBuildListPagingPresenter] using this listener to build list of data.
 */
interface PagingEventsListener<Key : Any, Data : Any> {

    suspend fun onEvents(events: List<PagingEvent<Key, Data>>)

    suspend fun onEvent(event: PagingEvent<Key, Data>) {
        onEvents(listOf(event))
    }
}
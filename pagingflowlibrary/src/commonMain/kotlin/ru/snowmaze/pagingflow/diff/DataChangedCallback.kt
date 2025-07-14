package ru.snowmaze.pagingflow.diff

import ru.snowmaze.pagingflow.presenters.BasicBuildListPagingPresenter

/**
 * This callback called when data is changed.
 * Default presenters like [BasicBuildListPagingPresenter] using this callback to build list of data.
 */
interface DataChangedCallback<Key : Any, Data : Any> {

    suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>)

    suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
        onEvents(listOf(event))
    }
}
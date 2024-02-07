package ru.snowmaze.pagingflow.diff

/**
 * This callback called when data is changed.
 * Default presenters like [SimplePagingDataPresenter] using this callback to build list of data.
 */
interface DataChangedCallback<Key : Any, Data : Any> {

    fun onEvents(events: List<DataChangedEvent<Key, Data>>)

    fun onEvent(event: DataChangedEvent<Key, Data>) {
        onEvents(listOf(event))
    }
}
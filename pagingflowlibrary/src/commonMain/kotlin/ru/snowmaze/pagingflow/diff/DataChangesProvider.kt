package ru.snowmaze.pagingflow.diff

interface DataChangesProvider<Key : Any, Data : Any> {

    /**
     * Adds callback which called when data has been changed
     */
    fun addDataChangedCallback(callback: PagingEventsListener<Key, Data>)

    /**
     * Removes data changed callback
     */
    fun removeDataChangedCallback(callback: PagingEventsListener<Key, Data>)
}
package ru.snowmaze.pagingflow.diff

interface DataChangesProvider<Key : Any, Data : Any> {

    /**
     * Adds callback which called when data has been changed
     */
    fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>)

    /**
     * Removes data changed callback
     */
    fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>)
}
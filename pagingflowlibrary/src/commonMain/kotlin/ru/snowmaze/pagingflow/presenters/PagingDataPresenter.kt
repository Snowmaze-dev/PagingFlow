package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.StateFlow
import ru.snowmaze.pagingflow.diff.DataChangedCallback

abstract class PagingDataPresenter<Key : Any, Data : Any> {

    protected val dataChangedCallbacks = mutableListOf<DataChangedCallback<Key, Data>>()

    abstract val dataFlow: StateFlow<List<Data?>>

    /**
     * Adds callback which called when data has been changed
     */
    fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataChangedCallbacks.add(callback)
    }

    /**
     * Removes data changed callback
     */
    fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataChangedCallbacks.remove(callback)
    }

    protected inline fun callDataChangedCallbacks(block: DataChangedCallback<Key, Data>.() -> Unit) {
        dataChangedCallbacks.forEach(block)
    }
}

val PagingDataPresenter<*, *>.itemCount get() = dataFlow.value.size

fun <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.mapNotNull(
) = dataFlow as StateFlow<List<Data>>
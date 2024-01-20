package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.StateFlow
import ru.snowmaze.pagingflow.diff.DataChangedCallback

/**
 * Base class for paging presenter, which exposes full list of data and callbacks to subscribe for data changes
 */
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

    protected fun createDefaultDataChangedCallback(
    ) = object : DataChangedCallback<Key, Data> {

        override fun onPageAdded(
            key: Key?,
            pageIndex: Int,
            sourceIndex: Int,
            items: List<Data>
        ) = callDataChangedCallbacks { onPageAdded(key, pageIndex, sourceIndex, items) }

        override fun onPageChanged(
            key: Key?,
            pageIndex: Int,
            sourceIndex: Int,
            items: List<Data?>
        ) = callDataChangedCallbacks { onPageChanged(key, pageIndex, sourceIndex, items) }

        override fun onPageRemoved(key: Key?, pageIndex: Int, sourceIndex: Int) {
            callDataChangedCallbacks { onPageRemoved(key, pageIndex, sourceIndex) }
        }

        override fun onInvalidate() {
            callDataChangedCallbacks { onInvalidate() }
        }
    }
}

val PagingDataPresenter<*, *>.itemCount get() = dataFlow.value.size

fun <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.mapNotNull(
) = dataFlow as StateFlow<List<Data>>
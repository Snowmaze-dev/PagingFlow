package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.DataChangedCallback

abstract class DefaultDataChangesMedium<Key : Any, Data : Any> : DataChangesMedium<Key, Data> {

    protected val dataChangedCallbacks = mutableListOf<DataChangedCallback<Key, Data>>()

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataChangedCallbacks.add(callback)
    }

    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataChangedCallbacks.remove(callback)
    }

    protected inline fun callDataChangedCallbacks(
        block: DataChangedCallback<Key, Data>.() -> Unit
    ) {
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
package ru.snowmaze.pagingflow.diff

import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.presenters.SimplePagingDataPresenter

/**
 * This callback called when data is changed.
 * Default presenters like [SimplePagingDataPresenter] using this callback to build list of data.
 */
interface DataChangedCallback<Key : Any, Data : Any> {

    fun onPageAdded(key: Key?, pageIndex: Int, sourceIndex: Int, items: List<Data>) {}

    fun onPageChanged(key: Key?, pageIndex: Int, sourceIndex: Int, items: List<Data?>) {}

    fun onPageRemoved(key: Key?, pageIndex: Int, sourceIndex: Int) {}

    /**
     * @see [PagingFlow.invalidate]
     */
    fun onInvalidate() {}
}
package ru.snowmaze.pagingflow.internal

import androidx.collection.MutableObjectList
import androidx.collection.ObjectList
import ru.snowmaze.pagingflow.source.PagingSource
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.utils.elementAtOrNull

internal class PagingSourcesManager<Key : Any, Data : Any> {

    private val _downPagingSources = MutableObjectList<PagingSource<Key, Data>>()
    val downPagingSources: ObjectList<PagingSource<Key, Data>> get() = _downPagingSources
    private val _upPagingSources = MutableObjectList<PagingSource<Key, Data>>()
    val upPagingSources = _upPagingSources

    fun replacePagingSources(pagingSourceList: List<PagingSource<Key, Data>>) {
        _downPagingSources.clear()
        _downPagingSources.addAll(pagingSourceList)
    }

    /**
     * Adds paging source to end of chain
     */
    fun addDownPagingSource(pagingSource: PagingSource<Key, Data>, index: Int? = null) {
        if (index == null) _downPagingSources.add(pagingSource)
        else _downPagingSources.add(index, pagingSource)
    }

    fun addUpPagingSource(pagingSource: PagingSource<Key, Data>) {
        _upPagingSources.add(pagingSource)
    }

    /**
     * Removes paging source and invalidates all data
     */
    fun removePagingSource(pagingSource: PagingSource<Key, Data>) {
        _downPagingSources.remove(pagingSource)
    }

    fun removePagingSource(dataSourceIndex: Int): Boolean {
        if (dataSourceIndex in 0 until _downPagingSources.size) {
            _downPagingSources.removeAt(dataSourceIndex)
            return true
        }
        return false
    }

    fun getSourceIndex(
        pagingSource: PagingSource<Key, Data>
    ) = _upPagingSources.indexOfFirst { it == pagingSource }.takeUnless { it == -1 }
        ?: _downPagingSources.indexOfFirst { it == pagingSource }

    fun findNextPagingSource(
        currentPagingSource: Pair<PagingSource<Key, Data>, Int>?,
        paginationDirection: PaginationDirection,
        isThereKey: Boolean
    ): Pair<PagingSource<Key, Data>, Int>? {
        if (isThereKey && currentPagingSource?.first != null) return currentPagingSource
        val sourceIndex = if (currentPagingSource?.first == null) {
            if (paginationDirection == PaginationDirection.DOWN) -1 else 0
        } else currentPagingSource.second
        val checkingIndex =
            sourceIndex + if (paginationDirection == PaginationDirection.DOWN) 1 else -1
        return (if (checkingIndex >= 0) downPagingSources.elementAtOrNull(checkingIndex)
        else _upPagingSources.elementAtOrNull(checkingIndex + 1))?.let { it to checkingIndex }
    }

    fun movePagingSource(oldIndex: Int, newIndex: Int) {
        val old = downPagingSources.elementAtOrNull(oldIndex) ?: return
        _downPagingSources.removeAt(oldIndex)
        _downPagingSources.add(newIndex.coerceAtMost(downPagingSources.size), old)
    }
}
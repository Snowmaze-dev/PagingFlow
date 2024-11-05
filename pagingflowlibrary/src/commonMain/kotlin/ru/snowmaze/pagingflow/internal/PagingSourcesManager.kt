package ru.snowmaze.pagingflow.internal

import ru.snowmaze.pagingflow.source.PagingSource
import ru.snowmaze.pagingflow.PaginationDirection

internal class PagingSourcesManager<Key : Any, Data : Any> {

    private val _pagingSources = mutableListOf<PagingSource<Key, Data>>()
    val pagingSources: List<PagingSource<Key, Data>> get() = _pagingSources

    fun replacePagingSources(pagingSourceList: List<PagingSource<Key, Data>>) {
        _pagingSources.clear()
        _pagingSources.addAll(pagingSourceList)
    }

    /**
     * Adds paging source to end of chain
     */
    fun addPagingSource(pagingSource: PagingSource<Key, Data>, index: Int? = null) {
        if (index == null) _pagingSources.add(pagingSource)
        else _pagingSources.add(index, pagingSource)
    }

    /**
     * Removes paging source and invalidates all data
     */
    fun removePagingSource(pagingSource: PagingSource<Key, Data>) {
        _pagingSources.remove(pagingSource)
    }

    fun removePagingSource(dataSourceIndex: Int): Boolean {
        _pagingSources.getOrNull(dataSourceIndex) ?: return false
        _pagingSources.removeAt(dataSourceIndex)
        return true
    }

    fun getSourceIndex(
        pagingSource: PagingSource<Key, Data>
    ) = pagingSources.indexOfFirst { it == pagingSource }

    fun findNextPagingSource(
        currentPagingSource: Pair<PagingSource<Key, Data>, Int>?,
        paginationDirection: PaginationDirection,
        isThereKey: Boolean
    ): Pair<PagingSource<Key, Data>, Int>? {
        if (isThereKey && currentPagingSource?.first != null) return currentPagingSource
        val sourceIndex = if (currentPagingSource?.first == null) -1
        else {
            val foundSourceIndex = getSourceIndex(currentPagingSource.first)
            if (foundSourceIndex == -1) throw IllegalStateException(
                "Cant find current data sources. Looks like bug in library. Report to developer."
            )
            foundSourceIndex
        }
        val checkingIndex =
            sourceIndex + if (paginationDirection == PaginationDirection.DOWN) 1 else -1
        return pagingSources.getOrNull(checkingIndex)?.let { it to checkingIndex }
    }

    fun movePagingSource(oldIndex: Int, newIndex: Int) {
        val old = pagingSources.getOrNull(oldIndex) ?: return
        _pagingSources.removeAt(oldIndex)
        _pagingSources.add(newIndex.coerceAtMost(pagingSources.size), old)
    }
}
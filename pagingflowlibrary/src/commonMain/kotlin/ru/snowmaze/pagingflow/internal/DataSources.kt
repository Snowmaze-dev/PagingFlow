package ru.snowmaze.pagingflow.internal

import ru.snowmaze.pagingflow.sources.DataSource
import ru.snowmaze.pagingflow.PaginationDirection

internal class DataSources<Key : Any, Data : Any, PagingStatus : Any> {

    private val _dataSources = mutableListOf<DataSource<Key, Data, PagingStatus>>()
    val dataSources: List<DataSource<Key, Data, PagingStatus>> get() = _dataSources

    /**
     * Adds data source to end of chain
     */
    fun addDataSource(dataSource: DataSource<Key, Data, PagingStatus>, index: Int? = null) {
        if (index == null) _dataSources.add(dataSource)
        else _dataSources.add(index, dataSource)
    }

    /**
     * Removes data source and invalidates all data
     */
    fun removeDataSource(dataSource: DataSource<Key, Data, PagingStatus>) {
        _dataSources.remove(dataSource)
    }

    fun removeDataSource(dataSourceIndex: Int): Boolean {
        _dataSources.getOrNull(dataSourceIndex) ?: return false
        _dataSources.removeAt(dataSourceIndex)
        return true
    }

    fun getSourceIndex(
        dataSource: DataSource<Key, Data, PagingStatus>
    ) = dataSources.indexOfFirst { it == dataSource }

    fun findNextDataSource(
        currentDataSource: Pair<DataSource<Key, Data, PagingStatus>, Int>?,
        paginationDirection: PaginationDirection,
        isThereKey: Boolean
    ): Pair<DataSource<Key, Data, PagingStatus>, Int>? {
        if (isThereKey && currentDataSource?.first != null) return currentDataSource
        val sourceIndex = if (currentDataSource?.first == null) -1
        else {
            val foundSourceIndex = getSourceIndex(currentDataSource.first)
            if (foundSourceIndex == -1) throw IllegalStateException(
                "Cant find current data sources. Looks like bug in library. Report to developer."
            )
            foundSourceIndex
        }
        val checkingIndex =
            sourceIndex + if (paginationDirection == PaginationDirection.DOWN) 1 else -1
        return dataSources.getOrNull(checkingIndex)?.let { it to checkingIndex }
    }

    fun moveDataSource(oldIndex: Int, newIndex: Int) {
        val old = dataSources.getOrNull(oldIndex) ?: return
        _dataSources.removeAt(oldIndex)
        _dataSources.add(newIndex.coerceAtMost(dataSources.size), old)
    }
}
package ru.snowmaze.pagingflow.internal

import ru.snowmaze.pagingflow.sources.DataSource
import ru.snowmaze.pagingflow.PaginationDirection

internal class DataSources<Key : Any, Data : Any> {

    private val _dataSources = mutableListOf<DataSource<Key, Data>>()
    val dataSources: List<DataSource<Key, Data>> get() = _dataSources

    fun replaceDataSources(dataSourceList: List<DataSource<Key, Data>>) {
        _dataSources.clear()
        _dataSources.addAll(dataSourceList)
    }

    /**
     * Adds data source to end of chain
     */
    fun addDataSource(dataSource: DataSource<Key, Data>, index: Int? = null) {
        if (index == null) _dataSources.add(dataSource)
        else _dataSources.add(index, dataSource)
    }

    /**
     * Removes data source and invalidates all data
     */
    fun removeDataSource(dataSource: DataSource<Key, Data>) {
        _dataSources.remove(dataSource)
    }

    fun removeDataSource(dataSourceIndex: Int): Boolean {
        _dataSources.getOrNull(dataSourceIndex) ?: return false
        _dataSources.removeAt(dataSourceIndex)
        return true
    }

    fun getSourceIndex(
        dataSource: DataSource<Key, Data>
    ) = dataSources.indexOfFirst { it == dataSource }

    fun findNextDataSource(
        currentDataSource: Pair<DataSource<Key, Data>, Int>?,
        paginationDirection: PaginationDirection,
        isThereKey: Boolean
    ): Pair<DataSource<Key, Data>, Int>? {
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
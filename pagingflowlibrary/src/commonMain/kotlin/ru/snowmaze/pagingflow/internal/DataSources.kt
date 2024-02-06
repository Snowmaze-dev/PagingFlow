package ru.snowmaze.pagingflow.internal

import ru.snowmaze.pagingflow.sources.DataSource
import ru.snowmaze.pagingflow.PaginationDirection

internal class DataSources<Key : Any, Data : Any, PagingStatus : Any> {

    private val _dataSources = mutableListOf<DataSource<Key, Data, PagingStatus>>()
    val dataSources: List<DataSource<Key, Data, PagingStatus>> get() = _dataSources

    /**
     * Adds data source to end of chain
     */
    fun addDataSource(dataSource: DataSource<Key, Data, PagingStatus>) {
        _dataSources.add(dataSource)
    }

    /**
     * Removes data source and invalidates all data
     */
    fun removeDataSource(dataSource: DataSource<Key, Data, PagingStatus>) {
        _dataSources.remove(dataSource)
    }

    fun findNextDataSource(
        currentDataSource: Pair<DataSource<Key, Data, PagingStatus>, Int>?,
        navigationDirection: PaginationDirection,
        isThereKey: Boolean
    ): Pair<DataSource<Key, Data, PagingStatus>, Int>? {
        if (isThereKey && currentDataSource?.first != null) return currentDataSource
        val sourceIndex = if (currentDataSource?.first == null) -1
        else {
            val foundSourceIndex = dataSources.indexOf(currentDataSource.first)
            if (foundSourceIndex == -1) throw IllegalStateException(
                "Cant find current data sources. Looks like bug in library. Report to developer."
            )
            foundSourceIndex
        }
        val checkingIndex =
            sourceIndex + if (navigationDirection == PaginationDirection.DOWN) 1 else -1
        return dataSources.getOrNull(checkingIndex)?.let { it to checkingIndex }
    }
}
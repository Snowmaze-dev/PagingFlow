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
        currentDataSource: DataSource<Key, Data, PagingStatus>?,
        navigationDirection: PaginationDirection,
        isThereKey: Boolean
    ): DataSource<Key, Data, PagingStatus>? {
        if (isThereKey && currentDataSource != null) return currentDataSource
        val sourceIndex = if (currentDataSource == null) -1
        else {
            val foundSourceIndex = dataSources.indexOf(currentDataSource)
            if (foundSourceIndex == -1) throw IllegalStateException(
                "Cant find current data sources. Looks like bug in library. Report to developer."
            )
            foundSourceIndex
        }
        return dataSources.getOrNull(
            sourceIndex + if (navigationDirection == PaginationDirection.DOWN) 1 else -1
        )
    }
}
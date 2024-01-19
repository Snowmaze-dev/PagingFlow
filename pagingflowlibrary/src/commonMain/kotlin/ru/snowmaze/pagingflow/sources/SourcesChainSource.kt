package ru.snowmaze.pagingflow.sources


/**
 * Data source which can load data from multiple chained data sources
 */
interface SourcesChainSource<Key : Any, Data : Any, PagingStatus : Any> :
    DataSource<Key, Data, PagingStatus> {

    /**
     * Adds data source to end of chain
     */
    fun addDataSource(dataSource: DataSource<Key, Data, PagingStatus>)

    /**
     * Removes data source and invalidates all data
     */
    fun removeDataSource(dataSource: DataSource<Key, Data, PagingStatus>)
}
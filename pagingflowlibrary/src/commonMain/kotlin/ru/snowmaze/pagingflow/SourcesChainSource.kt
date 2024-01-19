package ru.snowmaze.pagingflow

import kotlinx.coroutines.flow.StateFlow
import ru.snowmaze.pagingflow.sources.DataSource

interface SourcesChainSource<Key : Any, Data : Any, PagingStatus : Any> :
    DataSource<Key, Data, PagingStatus> {

    // TODO вынести все это в PagingFlow, убрать отсюда
    val pagingStatus: StateFlow<PagingStatus?>
    val isLoading: Boolean
    val currentPagesCount: Int
    val dataFlow: StateFlow<List<Data?>>
    // TODO

    // TODO
    fun invalidatePage(key: Key) {

    }

    fun addDataSource(dataSource: DataSource<Key, Data, PagingStatus>)

    fun removeDataSource(dataSource: DataSource<Key, Data, PagingStatus>)

    suspend fun invalidate()
}
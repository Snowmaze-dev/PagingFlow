package ru.snowmaze.pagingflow.sources

import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.LoadResult
import ru.snowmaze.pagingflow.PagingErrorsHandler

// Stateless source of paged data
interface DataSource<Key : Any, Data : Any, PagingStatus : Any> {

    val defaultLoadParams: LoadParams<Key>? get() = null

    val pagingErrorsHandler: PagingErrorsHandler<PagingStatus>? get() = null

    suspend fun loadData(loadParams: LoadParams<Key>): LoadResult<Key, Data, PagingStatus>
}
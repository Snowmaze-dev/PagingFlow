package ru.snowmaze.pagingflow.source

import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.errorshandler.PagingUnhandledErrorsHandler

/**
 * Stateless base provider of dynamic pageable data for pagination
 * You can override default [LoadParams] and [PagingUnhandledErrorsHandler] for each paging source
 */
fun interface PagingSource<Key : Any, Data : Any> {

    val defaultLoadParams: LoadParams<Key>? get() = null

    val pagingUnhandledErrorsHandler: PagingUnhandledErrorsHandler<Key, Data>? get() = null

    /**
     * Loads next page of data which can be updated when flow in result is updated
     */
    suspend fun load(loadParams: LoadParams<Key>): LoadResult<Key, Data>
}
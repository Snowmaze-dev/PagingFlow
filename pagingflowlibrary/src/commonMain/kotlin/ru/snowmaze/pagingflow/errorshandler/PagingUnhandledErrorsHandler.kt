package ru.snowmaze.pagingflow.errorshandler

import ru.snowmaze.pagingflow.result.LoadResult

/**
 * Error handler for unhandled errors from paging sources
 */
abstract class PagingUnhandledErrorsHandler<Key: Any, Data: Any> {

    abstract suspend fun handle(throwable: Throwable): LoadResult<Key, Data>
}
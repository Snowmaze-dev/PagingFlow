package ru.snowmaze.pagingflow.errorshandler

import ru.snowmaze.pagingflow.result.LoadResult

/**
 * Error handler for unhandled errors from data sources
 */
abstract class PagingUnhandledErrorsHandler<PagingStatus : Any> {

    abstract fun handle(throwable: Throwable): LoadResult.Failure<Any, Any, PagingStatus>
}
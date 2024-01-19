package ru.snowmaze.pagingflow.errorshandler

import ru.snowmaze.pagingflow.LoadResult

/**
 * Error handler for unhandled errors from data sources
 */
abstract class PagingUnhandledErrorsHandler<PagingStatus : Any> {

    abstract fun handle(exception: Exception): LoadResult.Failure<*, *, PagingStatus>
}
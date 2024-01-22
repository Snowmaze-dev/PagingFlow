package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.result.LoadResult

/**
 * Default error handler for unhandled errors from sources
 */
abstract class PagingErrorsHandler<PagingStatus : Any> {

    abstract fun handle(exception: Exception): LoadResult.Failure<*, *, PagingStatus>
}
package ru.snowmaze.pagingflow.errorshandler

import ru.snowmaze.pagingflow.LoadResult

class DefaultPagingUnhandledErrorsHandler<SourcePagingStatus : Any> :
    PagingUnhandledErrorsHandler<SourcePagingStatus>() {

    override fun handle(exception: Exception): LoadResult.Failure<*, *, SourcePagingStatus> {
        return LoadResult.Failure<Any, Any, Any>(
            status = null,
            additionalData = null,
            exception = exception
        ) as LoadResult.Failure<*, *, SourcePagingStatus>
    }
}
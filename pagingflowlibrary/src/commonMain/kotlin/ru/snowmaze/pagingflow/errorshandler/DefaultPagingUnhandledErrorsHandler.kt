package ru.snowmaze.pagingflow.errorshandler

import ru.snowmaze.pagingflow.LoadResult

class DefaultPagingUnhandledErrorsHandler<SourcePagingStatus : Any> :
    PagingUnhandledErrorsHandler<SourcePagingStatus>() {

    override fun handle(throwable: Throwable): LoadResult.Failure<*, *, SourcePagingStatus> {
        return LoadResult.Failure<Any, Any, Any>(
            status = null,
            additionalData = null,
            throwable = throwable
        ) as LoadResult.Failure<*, *, SourcePagingStatus>
    }
}
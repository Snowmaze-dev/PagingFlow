package ru.snowmaze.pagingflow.errorshandler

import ru.snowmaze.pagingflow.LoadResult

class DefaultPagingUnhandledErrorsHandler<SourcePagingStatus : Any> :
    PagingUnhandledErrorsHandler<SourcePagingStatus>() {

    override fun handle(throwable: Throwable): LoadResult.Failure<Any, Any, SourcePagingStatus> {
        return LoadResult.Failure(
            status = null,
            additionalData = null,
            throwable = throwable
        )
    }
}
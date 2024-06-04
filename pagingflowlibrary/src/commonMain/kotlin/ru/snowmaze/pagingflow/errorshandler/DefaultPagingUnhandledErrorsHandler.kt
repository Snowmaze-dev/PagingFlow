package ru.snowmaze.pagingflow.errorshandler

import ru.snowmaze.pagingflow.result.LoadResult

class DefaultPagingUnhandledErrorsHandler : PagingUnhandledErrorsHandler() {

    override fun handle(throwable: Throwable): LoadResult.Failure<Any, Any> {
        return LoadResult.Failure(
            returnData = null,
            throwable = throwable
        )
    }
}
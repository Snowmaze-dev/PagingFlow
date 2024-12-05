package ru.snowmaze.pagingflow.errorshandler

import ru.snowmaze.pagingflow.result.LoadResult

class DefaultPagingUnhandledErrorsHandler<Key : Any, Data : Any> :
    PagingUnhandledErrorsHandler<Key, Data>() {

    override suspend fun handle(throwable: Throwable): LoadResult<Key, Data> {
        return LoadResult.Failure(
            returnData = null,
            throwable = throwable
        )
    }
}
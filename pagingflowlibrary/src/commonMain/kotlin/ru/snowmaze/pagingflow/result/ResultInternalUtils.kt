package ru.snowmaze.pagingflow.result

import ru.snowmaze.pagingflow.params.PagingParams

internal fun <Key: Any, Data: Any> LoadResult<Key, Data>.mapParams(
    params: PagingParams?
) = when (this) {
    is LoadResult.Success -> LoadResult.Success(
        dataFlow = dataFlow,
        nextPageKey = nextPageKey,
        returnData = params,
        cachedResult = cachedResult
    )

    is LoadResult.Failure -> LoadResult.Failure(
        returnData = params,
        throwable = throwable
    )

    is LoadResult.NothingToLoad -> LoadResult.NothingToLoad(returnData = params)
}
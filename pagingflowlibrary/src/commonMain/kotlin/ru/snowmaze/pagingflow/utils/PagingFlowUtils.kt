package ru.snowmaze.pagingflow.utils

import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.result.LoadNextPageResult

suspend fun <Key : Any, Data : Any> PagingFlow<Key, Data>.loadPagesUntil(
    paginationDirection: PaginationDirection =
        pagingFlowConfiguration.defaultParamsProvider().paginationDirection,
    maxLoadPages: Int? = null,
    params: PagingParams? = null,
    shouldLoadNext: suspend (LoadNextPageResult.Success<Key, Data>) -> Boolean,
) {
    var result: LoadNextPageResult<Key, Data>
    var count = 0
    do {
        result = loadNextPageWithResult(paginationDirection, params)
        count++
    } while (result is LoadNextPageResult.Success<Key, Data> && shouldLoadNext(result)
        && (maxLoadPages == null || maxLoadPages > count)
    )
}
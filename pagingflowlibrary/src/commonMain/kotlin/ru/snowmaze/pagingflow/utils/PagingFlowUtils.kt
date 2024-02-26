package ru.snowmaze.pagingflow.utils

import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.result.LoadNextPageResult

suspend fun <Key : Any, Data : Any, PagingStatus : Any> PagingFlow<Key, Data, PagingStatus>.loadPagesUntil(
    maxLoadPages: Int? = null,
    shouldLoadNext: (LoadNextPageResult.Success<Key, Data>) -> Boolean,
) {
    var result: LoadNextPageResult<Key, Data>
    var count = 0
    do {
        result = loadNextPageWithResult()
        count++
    } while (result is LoadNextPageResult.Success<Key, Data> &&
        result.hasNext && shouldLoadNext(result)
        && (maxLoadPages == null || maxLoadPages > count)
    )
}
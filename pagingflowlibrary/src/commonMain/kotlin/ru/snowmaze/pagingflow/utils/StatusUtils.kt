package ru.snowmaze.pagingflow.utils

import ru.snowmaze.pagingflow.PagingStatus

internal inline fun <Key: Any> PagingStatus<Key>.mapHasNext(
    hasNext: Boolean,
) = when (this) {
    is PagingStatus.Success -> PagingStatus.Success(hasNext, currentKey)
    is PagingStatus.Initial -> PagingStatus.Initial(hasNext)
    else -> this
}
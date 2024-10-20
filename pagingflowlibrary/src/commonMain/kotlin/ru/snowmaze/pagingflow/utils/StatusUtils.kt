package ru.snowmaze.pagingflow.utils

import ru.snowmaze.pagingflow.PagingStatus

internal fun PagingStatus.mapHasNext(
    hasNext: Boolean,
) = when (this) {
    is PagingStatus.Success -> PagingStatus.Success(hasNext)
    is PagingStatus.Initial -> PagingStatus.Initial(hasNext)
    else -> this
}
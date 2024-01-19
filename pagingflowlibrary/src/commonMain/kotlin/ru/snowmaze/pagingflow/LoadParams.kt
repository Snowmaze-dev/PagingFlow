package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.params.PagingParams

data class LoadParams<T>(
    val pageSize: Int,
    val key: T? = null,
    val paginationDirection: PaginationDirection = PaginationDirection.DOWN,
    val pagingParams: PagingParams? = null,
    val cachedResult: PagingParams? = null
)
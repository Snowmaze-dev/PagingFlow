package ru.snowmaze.pagingflow

data class LoadParams<T>(
    val loadSize: Int,
    val key: T? = null,
    val paginationDirection: PaginationDirection = PaginationDirection.DOWN,
    val existingAdditionalData: Any? = null
)
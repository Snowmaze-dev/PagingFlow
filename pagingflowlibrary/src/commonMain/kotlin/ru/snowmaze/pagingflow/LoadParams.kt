package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.params.DataKey
import ru.snowmaze.pagingflow.params.MutablePagingParams
import ru.snowmaze.pagingflow.params.PagingParams

data class LoadParams<T>(
    val pageSize: Int,
    val key: T? = null,
    val paginationDirection: PaginationDirection = PaginationDirection.DOWN,
    val pagingParams: PagingParams? = null,
    val cachedResult: MutablePagingParams? = null
) {

    fun requireKey() = requireNotNull(key)

    fun requirePagingParams() = requireNotNull(pagingParams)
}

inline operator fun <Key, T> LoadParams<Key>.get(key: DataKey<T>) = requirePagingParams()[key]

inline fun <Key, T> LoadParams<Key>.getOrNull(
    key: DataKey<T>
) = pagingParams?.getOrNull(key)
package ru.snowmaze.pagingflow.result

import ru.snowmaze.pagingflow.params.PagingParams

sealed class LoadNextPageResult<Key> {

    abstract val returnData: PagingParams

    data class Success<Key : Any>(
        val currentKey: Key? = null,
        override val returnData: PagingParams,
        val hasNext: Boolean = false,
        val nextPageKey: Key? = null
    ) : LoadNextPageResult<Key>()

    data class Failure<Key : Any>(
        override val returnData: PagingParams,
        val throwable: Throwable
    ) : LoadNextPageResult<Key>()

    data class NothingToLoad<Key : Any>(
        override val returnData: PagingParams,
    ) : LoadNextPageResult<Key>()
}
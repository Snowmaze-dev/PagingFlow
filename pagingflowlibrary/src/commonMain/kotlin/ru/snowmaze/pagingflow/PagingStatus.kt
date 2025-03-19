package ru.snowmaze.pagingflow

sealed class PagingStatus<Key: Any> {

    abstract val hasNextPage: Boolean

    class Initial<Key: Any> internal constructor(
        override val hasNextPage: Boolean
    ) : PagingStatus<Key>()

    data class Success<Key: Any>(
        override val hasNextPage: Boolean,
        val currentKey: Key?
    ) : PagingStatus<Key>()

    data class Failure<Key: Any>(
        val throwable: Throwable,
        override val hasNextPage: Boolean
    ) : PagingStatus<Key>()

    data class Loading<Key: Any>(
        override val hasNextPage: Boolean
    ) : PagingStatus<Key>()
}
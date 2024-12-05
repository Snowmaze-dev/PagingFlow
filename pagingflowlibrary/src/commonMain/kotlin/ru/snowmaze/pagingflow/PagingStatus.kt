package ru.snowmaze.pagingflow

sealed class PagingStatus<Key: Any> {

    abstract val hasNextPage: Boolean

    class Initial<Key: Any> internal constructor(
        override val hasNextPage: Boolean
    ) : PagingStatus<Key>()

    data class Success<Key: Any>(
        override val hasNextPage: Boolean = true,
        val currentKey: Key?
    ) : PagingStatus<Key>()

    data class Failure<Key: Any>(
        val throwable: Throwable
    ) : PagingStatus<Key>() {

        override val hasNextPage = true
    }

    data object Loading : PagingStatus<Any>() {

        override val hasNextPage = true
    }
}
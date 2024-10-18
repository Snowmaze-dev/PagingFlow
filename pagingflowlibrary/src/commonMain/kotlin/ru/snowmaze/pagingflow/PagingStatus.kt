package ru.snowmaze.pagingflow

sealed class PagingStatus {

    abstract val hasNextPage: Boolean

    class Initial internal constructor(
        override val hasNextPage: Boolean
    ) : PagingStatus()

    data class Success(
        override val hasNextPage: Boolean = true
    ) : PagingStatus()

    data class Failure(
        val throwable: Throwable
    ) : PagingStatus() {

        override val hasNextPage = true
    }

    class Loading : PagingStatus() {

        override val hasNextPage = true
    }
}
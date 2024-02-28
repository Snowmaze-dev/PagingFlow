package ru.snowmaze.pagingflow

sealed class PagingStatus<SourcePagingStatus : Any> {

    abstract val hasNextPage: Boolean

    class Initial<SourcePagingStatus : Any> internal constructor(
    ) : PagingStatus<SourcePagingStatus>() {

        override val hasNextPage = true
    }

    data class Success<SourcePagingStatus : Any>(
        val sourcePagingStatus: SourcePagingStatus? = null,
        override val hasNextPage: Boolean = true
    ) : PagingStatus<SourcePagingStatus>()

    data class Failure<SourcePagingStatus : Any>(
        val sourcePagingStatus: SourcePagingStatus? = null,
        val throwable: Throwable
    ) : PagingStatus<SourcePagingStatus>() {

        override val hasNextPage = true
    }

    class Loading<SourcePagingStatus : Any> : PagingStatus<SourcePagingStatus>() {

        override val hasNextPage = true
    }
}
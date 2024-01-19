package ru.snowmaze.pagingflow

sealed class PagingStatus<SourcePagingStatus : Any>(val hasNextPage: Boolean) {

    class Success<SourcePagingStatus : Any>(
        val sourcePagingStatus: SourcePagingStatus? = null,
        hasNextPage: Boolean
    ) : PagingStatus<SourcePagingStatus>(hasNextPage)

    class Failure<SourcePagingStatus : Any>(
        val sourcePagingStatus: SourcePagingStatus? = null,
        val throwable: Throwable
    ) : PagingStatus<SourcePagingStatus>(true)

    class Loading<SourcePagingStatus : Any> : PagingStatus<SourcePagingStatus>(true)
}
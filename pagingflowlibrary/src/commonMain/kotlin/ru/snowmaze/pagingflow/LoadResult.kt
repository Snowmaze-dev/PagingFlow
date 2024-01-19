@file:OptIn(ExperimentalContracts::class)

package ru.snowmaze.pagingflow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.sources.DataSource
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * The class which holds result of loading data from [DataSource].
 */
sealed class LoadResult<Key : Any, Data : Any, SourcePagingStatus : Any> {

    abstract val status: SourcePagingStatus?
    abstract val additionalData: PagingParams?

    /**
     * Success result object for [DataSource.load]
     * @param [nextNextPageKey] key of next page of pagination, if end is reached you need to specify null instead of key
     * @param status which will be set to upPagingStatus or downPagingStatus
     * @param additionalData params which will be returned to caller of loading next page
     * @param cachedResult you can use cached result to reuse result of, for example, network operation and load data from database again.
     * It will be supplied with [LoadParams] with same key
     */
    data class Success<Key : Any, Data : Any, PagingStatus : Any>(
        val dataFlow: Flow<UpdatableData<Key, Data>>?, // You can specify null to use dataFlow from last page
        val nextNextPageKey: Key? = null,
        override val status: PagingStatus?,
        override val additionalData: PagingParams? = null,
        val cachedResult: PagingParams? = null,
    ) : LoadResult<Key, Data, PagingStatus>()

    /**
     * Failure result object for [DataSource.load]
     * @param status which will be set to upPagingStatus or downPagingStatus
     * @param additionalData params which will be returned to caller of loading next page
     * @param exception exception that occurred
     */
    data class Failure<Key : Any, Data : Any, PagingStatus : Any>(
        override val status: PagingStatus? = null,
        override val additionalData: PagingParams? = null,
        val exception: Exception
    ) : LoadResult<Key, Data, PagingStatus>()
}

/**
 * Extension function which helps to calculate next key based on current offset and pagination direction
 * @return next key or null if theres no next page or next key below 0
 */
fun DataSource<*, *, *>.positiveOffset(
    paginationDirection: PaginationDirection,
    currentOffset: Int,
    pageSize: Int,
    hasNextPage: Boolean = true
) = if (paginationDirection == PaginationDirection.DOWN) currentOffset + pageSize
else (currentOffset - pageSize).takeIf { it >= 0 }.takeIf { hasNextPage }

fun <Key : Any, Data : Any, PagingStatus : Any> LoadResult<Key, Data, PagingStatus>.mapSuccess(
    transform: (LoadResult.Success<Key, Data, PagingStatus>) -> LoadResult<Key, Data, PagingStatus>
): LoadResult<Key, Data, PagingStatus> {
    contract {
        callsInPlace(transform, InvocationKind.EXACTLY_ONCE)
    }
    return if (this is LoadResult.Success<Key, Data, PagingStatus>) transform(this) else this
}


fun <Key : Any, Data : Any, PagingStatus : Any> DataSource<Key, Data, PagingStatus>.simpleResult(
    data: List<Data>,
    nextPageKey: Key? = null,
    status: PagingStatus? = null,
    additionalData: PagingParams? = null,
    cachedResult: PagingParams? = null,
) = LoadResult.Success(
    dataFlow = flow { emit(UpdatableData(data, nextPageKey)) },
    nextNextPageKey = nextPageKey,
    status = status,
    additionalData = additionalData,
    cachedResult = cachedResult
)

fun <Key : Any, Data : Any, PagingStatus : Any> DataSource<Key, Data, PagingStatus>.result(
    dataFlow: Flow<List<Data>>,
    nextPageKey: Key? = null,
    status: PagingStatus? = null,
    additionalData: PagingParams? = null,
    cachedResult: PagingParams? = null,
) = LoadResult.Success(
    dataFlow = dataFlow.map { UpdatableData(it, null) },
    nextNextPageKey = nextPageKey,
    status = status,
    additionalData = additionalData,
    cachedResult = cachedResult
)

fun <Key : Any, Data : Any, PagingStatus : Any> DataSource<Key, Data, PagingStatus>.useLastPageResult(
    nextPageKey: Key? = null,
    status: PagingStatus? = null,
    additionalData: PagingParams? = null,
    cachedResult: PagingParams? = null,
) = LoadResult.Success<Key, Data, PagingStatus>(
    dataFlow = null,
    nextNextPageKey = nextPageKey,
    status = status,
    additionalData = additionalData,
    cachedResult = cachedResult
)
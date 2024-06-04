@file:OptIn(ExperimentalContracts::class)

package ru.snowmaze.pagingflow.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.sources.DataSource
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Extension function which helps to calculate next key based on current offset and pagination direction
 * @return next key or null if theres no next page or next key below 0
 */
fun DataSource<*, *>.positiveOffset(
    paginationDirection: PaginationDirection,
    currentOffset: Int,
    pageSize: Int,
    hasNextPage: Boolean = true
) = (if (paginationDirection == PaginationDirection.DOWN) currentOffset + pageSize
else (currentOffset - pageSize).takeIf { it >= 0 }).takeIf { hasNextPage }

fun LoadParams<Int>.positiveOffset(
    hasNextPage: Boolean = true,
    currentOffset: Int = key ?: 0,
    pageSize: Int = this.pageSize
): Int? {
    return (if (paginationDirection == PaginationDirection.DOWN) currentOffset + pageSize
    else (currentOffset - pageSize).takeIf { it >= 0 }).takeIf { hasNextPage }
}

fun <Key : Any, Data : Any> LoadResult<Key, Data>.mapSuccess(
    transform: (LoadResult.Success<Key, Data>) -> LoadResult<Key, Data>
): LoadResult<Key, Data> {
    contract {
        callsInPlace(transform, InvocationKind.EXACTLY_ONCE)
    }
    return if (this is LoadResult.Success<Key, Data>) transform(this) else this
}

fun <T : Any, Key : Any, Data : Any> Result<T>.toLoadResult(
    onFailure: (Throwable) -> LoadResult<Key, Data> = {
        LoadResult.Failure(throwable = it)
    },
    onSuccess: (T) -> LoadResult<Key, Data>
): LoadResult<Key, Data> = fold(
    onSuccess = onSuccess,
    onFailure = onFailure
)

fun <Key : Any, Data : Any> DataSource<Key, Data>.simpleResult(
    data: List<Data>,
    nextPageKey: Key? = null,
    additionalData: PagingParams? = null,
    cachedResult: PagingParams? = null,
) = LoadResult.Success(
    dataFlow = flow { emit(UpdatableData(data, nextPageKey, additionalData)) },
    nextPageKey = nextPageKey,
    returnData = additionalData,
    cachedResult = cachedResult
)

fun <Key : Any, Data : Any> DataSource<Key, Data>.result(
    dataFlow: Flow<List<Data>>,
    nextPageKey: Key? = null,
    additionalData: PagingParams? = null,
    cachedResult: PagingParams? = null,
) = LoadResult.Success(
    dataFlow = dataFlow.map { UpdatableData(it, nextPageKey, additionalData) },
    nextPageKey = nextPageKey,
    returnData = additionalData,
    cachedResult = cachedResult
)

fun <Key : Any, Data : Any> DataSource<Key, Data>.useLastPageResult(
    nextPageKey: Key? = null,
    additionalData: PagingParams? = null,
    cachedResult: PagingParams? = null,
) = LoadResult.Success<Key, Data>(
    dataFlow = null,
    nextPageKey = nextPageKey,
    returnData = additionalData,
    cachedResult = cachedResult
)
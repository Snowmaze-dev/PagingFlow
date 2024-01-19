@file:OptIn(ExperimentalContracts::class)

package ru.snowmaze.pagingflow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.snowmaze.pagingflow.sources.DataSource
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

sealed class LoadResult<Key : Any, Data : Any, PagingStatus : Any> {

    abstract val status: PagingStatus?
    abstract val additionalData: Any?

    data class Success<Key : Any, Data : Any, PagingStatus : Any>(
        val dataFlow: Flow<UpdatableData<Key, Data>>?, // You can specify null to use dataFlow from last page
        val nextNextPageKey: Key? = null,
        override val status: PagingStatus?,
        override val additionalData: Any? = null,
    ) : LoadResult<Key, Data, PagingStatus>()

    data class Failure<Key : Any, Data : Any, PagingStatus : Any>(
        override val status: PagingStatus? = null,
        override val additionalData: Any? = null,
        val exception: Exception? = null
    ) : LoadResult<Key, Data, PagingStatus>()
}

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
    additionalData: Any? = null
) = LoadResult.Success(
    dataFlow = flow { emit(UpdatableData(data, nextPageKey)) },
    nextNextPageKey = nextPageKey,
    status = status,
    additionalData = additionalData
)

fun <Key : Any, Data : Any, PagingStatus : Any> DataSource<Key, Data, PagingStatus>.result(
    dataFlow: Flow<UpdatableData<Key, Data>>,
    nextPageKey: Key? = null,
    status: PagingStatus? = null,
    additionalData: Any? = null
) = LoadResult.Success(
    dataFlow = dataFlow,
    nextNextPageKey = nextPageKey,
    status = status,
    additionalData = additionalData
)

fun <Key : Any, Data : Any, PagingStatus : Any> DataSource<Key, Data, PagingStatus>.useLastPageResult(
    nextPageKey: Key? = null,
    status: PagingStatus? = null,
    additionalData: Any? = null
) = LoadResult.Success<Key, Data, PagingStatus>(
    dataFlow = null,
    nextNextPageKey = nextPageKey,
    status = status,
    additionalData = additionalData
)
package ru.snowmaze.pagingflow.result

import kotlinx.coroutines.flow.Flow
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.params.PagingParams

sealed class LoadNextPageResult<Key, Data> {

    abstract val returnData: PagingParams

    data class Success<Key : Any, Data : Any>(
        val currentKey: Key? = null,
        val dataFlow: Flow<UpdatableData<Key, Data>>? = null,
        override val returnData: PagingParams,
        val hasNext: Boolean = false,
        val nextPageKey: Key? = null
    ) : LoadNextPageResult<Key, Data>()

    data class Failure<Key : Any, Data : Any>(
        override val returnData: PagingParams,
        val throwable: Throwable
    ) : LoadNextPageResult<Key, Data>()

    data class NothingToLoad<Key : Any, Data : Any>(
        override val returnData: PagingParams,
    ) : LoadNextPageResult<Key, Data>()
}
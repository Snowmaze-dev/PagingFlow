package ru.snowmaze.pagingflow

import kotlinx.coroutines.flow.Flow

sealed class LoadNextPageResult<Key, Data> {

    abstract val additionalData: Any?

    data class Success<Key : Any, Data : Any>(
        val currentKey: Key? = null,
        val dataFlow: Flow<UpdatableData<Key, Data>>? = null,
        override val additionalData: Any? = null,
        val hasNext: Boolean = false,
    ) : LoadNextPageResult<Key, Data>()

    data class Failure<Key : Any, Data : Any>(
        override val additionalData: Any? = null,
        val exception: Exception? = null
    ) : LoadNextPageResult<Key, Data>()
}
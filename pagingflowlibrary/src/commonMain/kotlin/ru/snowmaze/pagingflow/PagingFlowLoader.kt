package ru.snowmaze.pagingflow

import kotlinx.coroutines.flow.StateFlow
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.source.ConcatPagingSource

sealed interface PagingFlowLoader<Key: Any> {

    val pagingFlowConfiguration: PagingFlowConfiguration<Key>

    val downPagingStatus: StateFlow<PagingStatus<Key>>

    val upPagingStatus: StateFlow<PagingStatus<Key>>

    val pagesCount: Int

    val notNullifiedPagesCount: Int

    /**
     * @see [ConcatPagingSource.invalidate]
     */
    suspend fun invalidate(
        invalidateBehavior: InvalidateBehavior? = null,
        removeCachedData: Boolean = false,
    )
}
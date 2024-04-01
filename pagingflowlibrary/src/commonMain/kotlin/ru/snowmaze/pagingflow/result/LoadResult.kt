@file:OptIn(ExperimentalContracts::class)

package ru.snowmaze.pagingflow.result

import kotlinx.coroutines.flow.Flow
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.sources.DataSource
import kotlin.contracts.ExperimentalContracts

/**
 * The class which holds result of loading data from [DataSource].
 */
sealed class LoadResult<Key : Any, Data : Any, SourcePagingStatus : Any> {

    abstract val status: SourcePagingStatus?
    abstract val additionalData: PagingParams?

    /**
     * Success result object for [DataSource.load]
     * @param [nextPageKey] key of next page of pagination, if end is reached you need to specify null instead of key
     * @param status which will be set to upPagingStatus or downPagingStatus
     * @param additionalData params which will be returned to caller of loading next page
     * @param cachedResult you can use cached result to reuse result of, for example, network operation and load data from database again.
     * It will be supplied with [LoadParams] with same key
     */
    data class Success<Key : Any, Data : Any, PagingStatus : Any>(
        val dataFlow: Flow<UpdatableData<Key, Data>>?, // You can specify null to use dataFlow from last page
        val nextPageKey: Key? = null,
        override val status: PagingStatus? = null,
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
        val throwable: Throwable
    ) : LoadResult<Key, Data, PagingStatus>()

    /**
     * Result object for [DataSource.load] which indicates that theres is nothing to load now
     */
    data class NothingToLoad<Key : Any, Data : Any, PagingStatus : Any>(
        override val status: PagingStatus? = null,
        override val additionalData: PagingParams? = null
    ) : LoadResult<Key, Data, PagingStatus>()
}
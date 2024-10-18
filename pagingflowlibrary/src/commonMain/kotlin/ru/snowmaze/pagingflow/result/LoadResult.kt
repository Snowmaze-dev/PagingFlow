package ru.snowmaze.pagingflow.result

import kotlinx.coroutines.flow.Flow
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.sources.DataSource

/**
 * The class which holds result of loading data from [DataSource].
 */
sealed class LoadResult<Key : Any, Data : Any> {

    abstract val returnData: PagingParams?

    /**
     * Success result object for [DataSource.load]
     * @param [nextPageKey] key of next page of pagination, if end is reached you need to specify null instead of key
     * @param returnData params which will be returned to caller of loading next page
     * @param cachedResult you can use cached result to reuse result of, for example, network operation and load data from database again.
     * It will be supplied with [LoadParams] with same key
     */
    data class Success<Key : Any, Data : Any>(
        val dataFlow: Flow<UpdatableData<Key, Data>>?, // You can specify null to use dataFlow from last page
        val nextPageKey: Key? = null,
        override val returnData: PagingParams? = null,
        val cachedResult: PagingParams? = null,
    ) : LoadResult<Key, Data>()

    /**
     * Failure result object for [DataSource.load]
     * @param returnData params which will be returned to caller of loading next page
     * @param throwable exception that occurred
     */
    data class Failure<Key : Any, Data : Any>(
        override val returnData: PagingParams? = null,
        val throwable: Throwable
    ) : LoadResult<Key, Data>()

    /**
     * Result object for [DataSource.load] which indicates that theres is nothing to load now
     */
    data class NothingToLoad<Key : Any, Data : Any>(
        override val returnData: PagingParams? = null
    ) : LoadResult<Key, Data>()
}
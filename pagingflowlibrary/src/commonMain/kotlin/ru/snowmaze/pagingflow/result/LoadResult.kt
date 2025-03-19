package ru.snowmaze.pagingflow.result

import kotlinx.coroutines.flow.Flow
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.params.MutablePagingParams
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.source.PagingSource

/**
 * The class which holds result of loading data from [PagingSource].
 */
sealed class LoadResult<Key : Any, Data : Any> {

    abstract val returnData: PagingParams?

    /**
     * Success result object for [PagingSource.load]
     * @property nextPageKey key of next page of pagination, if end is reached you need to specify null instead of key
     * @property returnData params which will be returned to caller of loading next page
     * @property cachedResult you can use cached result to reuse result of, for example, network operation and load data from database again.
     * It will be supplied with [LoadParams] with same key
     */
    sealed class Success<Key : Any, Data : Any>: LoadResult<Key, Data>() {

        abstract val nextPageKey: Key?
        abstract val cachedResult: MutablePagingParams?

        data class FlowSuccess<Key : Any, Data : Any>(
            val dataFlow: Flow<UpdatableData<Key, Data>>?, // You can specify null to use dataFlow from last page
            override val nextPageKey: Key? = null,
            override val returnData: PagingParams? = null,
            override val cachedResult: MutablePagingParams? = null,
        ) : Success<Key, Data>()

        /**
         * Result with data list instead Flow, it allows faster data set if page not changes
         */
        data class SimpleSuccess<Key : Any, Data : Any>(
            val data: List<Data?>?,
            override val nextPageKey: Key? = null,
            override val returnData: MutablePagingParams? = null,
            override val cachedResult: MutablePagingParams? = null,
        ) : Success<Key, Data>()
    }

    /**
     * Failure result object for [PagingSource.load]
     * @param returnData params which will be returned to caller of loading next page
     * @param throwable exception that occurred
     */
    data class Failure<Key : Any, Data : Any>(
        override val returnData: PagingParams? = null,
        val throwable: Throwable
    ) : LoadResult<Key, Data>()

    /**
     * Result object for [PagingSource.load] which indicates that theres is nothing to load now
     */
    data class NothingToLoad<Key : Any, Data : Any>(
        override val returnData: PagingParams? = null
    ) : LoadResult<Key, Data>()
}
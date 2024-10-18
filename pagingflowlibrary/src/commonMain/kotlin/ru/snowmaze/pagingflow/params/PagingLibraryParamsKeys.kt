package ru.snowmaze.pagingflow.params

import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.sources.ConcatDataSource
import ru.snowmaze.pagingflow.sources.DataSource

object PagingLibraryParamsKeys {

    /**
     * Invalidates all data except new page in [ConcatDataSource]
     */
    object InvalidateData : DataKey<Boolean> {

        override val key = "invalidate_data"
    }

    /**
     * Specifies that loadNextPage should return a job
     * which awaits until first data event received from [DataSource.load] is handled by presenter
     * You can supply this param to [PagingFlow.loadNextPageWithResult] params
     * Then to get job you can use key [ReturnPagingLibraryKeys.DataSetJob]
     */
    object ReturnAwaitJob : DataKey<Boolean> {
        override val key = "await_data_load"
    }

    /**
     * Load several pages of data and only then sends data to presenter
     *
     * You can specify it like that:
     *```
     * PagingParams(
     *     LoadSeveralPages to LoadSeveralPagesData(
     *         getPagingParams = { result -> ... },
     *         onSuccess = { result -> ... }
     *     )
     * )
     *```
     *
     * After loading loadNextPageWithResult returns result with array of all result paging params
     * You can use key [ReturnPagingLibraryKeys.PagingParamsList] to get it
     * You can get these like that:
     * ```
     * result.returnData.get(ReturnPagingLibraryKeys.PagingParamsList)
     * ```
     *
     * @see LoadSeveralPagesData
     */
    object LoadSeveralPages : DataKey<LoadSeveralPagesData<Any, Any>> {
        override val key = "load_several_pages"
    }
}

/**
 * Model that holds lambdas for loading several pages of data
 * @param getPagingParams gets loading result (or null if its first pagination) and returns PagingParams to load another page or null if loading should stop
 * @param onSuccess called on successful load
 */
class LoadSeveralPagesData<Key : Any, Data : Any>(
    val getPagingParams: (LoadResult<Key, Data>?) -> PagingParams?,
    val onSuccess: ((LoadResult.Success<Key, Data>) -> Unit)? = null
)
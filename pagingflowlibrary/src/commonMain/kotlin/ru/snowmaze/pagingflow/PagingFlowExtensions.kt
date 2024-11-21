package ru.snowmaze.pagingflow

import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.snowmaze.pagingflow.params.LoadSeveralPagesData
import ru.snowmaze.pagingflow.params.MutablePagingParams
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.utils.fastForEach

/**
 * Loads next page
 * @return result of loading
 */
suspend fun <Key : Any, Data : Any> PagingFlow<Key, Data>.loadNextPageWithResult(
    paginationDirection: PaginationDirection? = null,
    pagingParams: MutablePagingParams? = null
) = load(paginationDirection, pagingParams)

/**
 * Loads next page async
 * @return loading job
 */
fun <Key : Any, Data : Any> PagingFlow<Key, Data>.loadNextPage(
    paginationDirection: PaginationDirection? = null,
    pagingParams: MutablePagingParams? = null
) = pagingFlowConfiguration.coroutineScope.launch {
    load(paginationDirection, pagingParams)
}

/**
 * Loads multiple pages at once and only then delivers events to change listeners
 *
 * @param paginationDirection the direction in which to load pages.
 * @param awaitDataSet if true, waits for the data to be set to the presenter
 * @param awaitTimeout the maximum time, in milliseconds, to wait for [awaitDataSet] completion.
 * @param pagingParams params of loading
 * @param onSuccess callback invoked with the result of a successful page load.
 * @param getPagingParams a lambda that decides if additional pages should be loaded. If it returns non-null [PagingParams],
 * the next page is loaded with these params; otherwise, loading stops.
 *
 * @return result of loading
 */
suspend fun <Key : Any, Data : Any> PagingFlow<Key, Data>.loadSeveralPages(
    paginationDirection: PaginationDirection = pagingFlowConfiguration.defaultParamsProvider()
        .paginationDirection,
    awaitDataSet: Boolean = false,
    awaitTimeout: Long? = null,
    pagingParams: MutablePagingParams? = null,
    onSuccess: ((LoadResult.Success<Key, Data>) -> Unit)? = null,
    getPagingParams: suspend (LoadResult<Key, Data>?) -> MutablePagingParams?,
): LoadNextPageResult<Key> {
    val result = load(
        paginationDirection = paginationDirection,
        pagingParams = (pagingParams ?: MutablePagingParams(1)).apply {
            put(
                PagingLibraryParamsKeys.LoadSeveralPages, LoadSeveralPagesData(
                    getPagingParams = {
                        val pageLoadParams = getPagingParams(it) ?: return@LoadSeveralPagesData null
                        if (awaitDataSet) {
                            pageLoadParams.put(PagingLibraryParamsKeys.ReturnAwaitJob, true)
                        }
                        pageLoadParams
                    },
                    onSuccess = onSuccess
                ) as LoadSeveralPagesData<Any, Any>
            )
        }
    )
    if (awaitDataSet) {
        val awaitData = suspend {
            result.returnData.getOrNull(ReturnPagingLibraryKeys.PagingParamsList)?.fastForEach {
                it?.getOrNull(ReturnPagingLibraryKeys.DataSetJob)?.join()
            }
        }
        if (awaitTimeout == null) awaitData()
        else withTimeoutOrNull(awaitTimeout) { awaitData() }
    }
    return result
}

/**
 * Loads page and then suspends until data is set to presenter.
 *
 * If [timeout] is passed then it will stop waiting if this timeout was exceeded.
 *
 * @param paginationDirection the direction in which to load pages.
 * @param timeout the maximum time, in milliseconds, to wait for data set to presenter.
 * @param pagingParams params of loading
 *
 * @return result of loading
 */
suspend fun <Key : Any, Data : Any> PagingFlow<Key, Data>.loadNextPageAndAwaitDataSet(
    paginationDirection: PaginationDirection = pagingFlowConfiguration.defaultParamsProvider()
        .paginationDirection,
    timeout: Long? = null,
    pagingParams: MutablePagingParams? = null
): LoadNextPageResult<Key> {
    val result = load(
        paginationDirection = paginationDirection,
        pagingParams = (pagingParams ?: MutablePagingParams(1)).apply {
            put(PagingLibraryParamsKeys.ReturnAwaitJob, true)
        }
    )
    val awaitData = suspend {
        result.returnData.getOrNull(ReturnPagingLibraryKeys.DataSetJob)?.join()
    }
    if (timeout == null) awaitData()
    else withTimeoutOrNull(timeout) { awaitData() }
    return result
}
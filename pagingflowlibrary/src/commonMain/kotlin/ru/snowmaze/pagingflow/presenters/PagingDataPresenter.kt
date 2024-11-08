package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.snowmaze.pagingflow.params.PagingParams

/**
 * Base class for paging presenter, which exposes full list of data and callbacks to subscribe for data changes
 */
interface PagingDataPresenter<Key : Any, Data : Any> {

    val latestDataFlow: Flow<LatestData<Data>>

    val latestData: LatestData<Data>

    // An index that takes into account null pages at the beginning
    val startIndex: Int
}

inline val <Key: Any, Data: Any> PagingDataPresenter<Key, Data>.dataFlow
    get() = latestDataFlow.map { it.data }

inline val <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.data
    get() = latestData.data

class LatestData<Data : Any>(
    val data: List<Data?>,
    val recentLoadData: List<PagingParams> = listOf(PagingParams.EMPTY)
) {

    inline val notNullData get() = data as List<Data>
}

inline val PagingDataPresenter<*, *>.itemCount get() = latestData.data.size

inline val <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.notNullFlow
    get() = latestDataFlow.map { it.notNullData }

inline val <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.notNullData
    get() = latestData.notNullData
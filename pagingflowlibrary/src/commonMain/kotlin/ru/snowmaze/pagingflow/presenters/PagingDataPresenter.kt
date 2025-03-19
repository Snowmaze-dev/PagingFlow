package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import ru.snowmaze.pagingflow.params.MutablePagingParams

/**
 * Base class for paging presenter, which exposes full list of data and callbacks to subscribe for data changes
 */
interface PagingDataPresenter<Key : Any, Data : Any> {

    val latestDataFlow: Flow<LatestData<Data>>

    // An index that takes into account null pages at the beginning
    // TODO add start index for both directions
    val startIndex: Int
}

interface StatePagingDataPresenter<Key : Any, Data : Any> : PagingDataPresenter<Key, Data> {

    override val latestDataFlow: StateFlow<LatestData<Data>>
}

inline val <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.dataFlow
    get() = latestDataFlow.map { it.data }

inline val <Key : Any, Data : Any> StatePagingDataPresenter<Key, Data>.data
    get() = latestDataFlow.value.data

class LatestData<Data : Any>(
    val data: List<Data?>,
    val loadData: List<MutablePagingParams> = emptyList()
) {

    inline val notNullData get() = data as List<Data>
}

inline val StatePagingDataPresenter<*, *>.itemCount get() = latestDataFlow.value.data.size

inline val <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.notNullFlow
    get() = latestDataFlow.map { it.notNullData }

inline val <Key : Any, Data : Any> StatePagingDataPresenter<Key, Data>.notNullData
    get() = latestDataFlow.value.notNullData
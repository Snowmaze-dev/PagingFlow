package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.StateFlow

/**
 * Base class for paging presenter, which exposes full list of data and callbacks to subscribe for data changes
 */
interface PagingDataPresenter<Key : Any, Data : Any> {

    val dataFlow: StateFlow<List<Data?>>

    // An index that takes into account null pages at the beginning
    val startIndex: Int
}

val PagingDataPresenter<*, *>.itemCount get() = dataFlow.value.size

fun <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.asNotNull(
) = dataFlow as StateFlow<List<Data>>
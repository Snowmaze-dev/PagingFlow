package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.StateFlow

/**
 * Base class for paging presenter, which exposes full list of data and callbacks to subscribe for data changes
 */
abstract class PagingDataPresenter<Key : Any, Data : Any> {

    abstract val dataFlow: StateFlow<List<Data?>>
}

val PagingDataPresenter<*, *>.itemCount get() = dataFlow.value.size

fun <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.asNotNull(
) = dataFlow as StateFlow<List<Data>>
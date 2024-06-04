package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.Flow

/**
 * Base class for paging presenter, which exposes full list of data and callbacks to subscribe for data changes
 */
interface PagingDataPresenter<Key : Any, Data : Any> {

    val dataFlow: Flow<List<Data?>> // TODO можно тут ещё флоу с доп.данными приделать или в нижестоящих презентерах

    val data: List<Data?>

    // An index that takes into account null pages at the beginning
    val startIndex: Int
}

val PagingDataPresenter<*, *>.itemCount get() = data.size

inline val <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.notNullFlow
    get() = dataFlow as Flow<List<Data>>

inline val <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.notNullData
    get() = data as List<Data>
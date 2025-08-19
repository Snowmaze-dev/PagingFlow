package ru.snowmaze.pagingflow.presenters.list

import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.params.MutablePagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior

interface ListBuildStrategy<Key : Any, Data : Any> {

    val startPageIndex: Int
    val recentLoadData: List<MutablePagingParams>

    fun buildList(
        events: List<PagingEvent<Key, Data>>,
        onInvalidate: (InvalidateBehavior?) -> Unit
    ): List<Data?>

    fun invalidate()
}
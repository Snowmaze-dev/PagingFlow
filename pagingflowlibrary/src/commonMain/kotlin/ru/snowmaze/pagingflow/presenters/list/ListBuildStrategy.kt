package ru.snowmaze.pagingflow.presenters.list

import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior

interface ListBuildStrategy<Key : Any, Data : Any> {

    val list: List<Data?>
    val startPageIndex: Int
    val recentLoadData: List<PagingParams>

    fun buildList(
        events: List<DataChangedEvent<Key, Data>>,
        onInvalidate: (InvalidateBehavior?) -> Unit
    )

    fun invalidate()
}
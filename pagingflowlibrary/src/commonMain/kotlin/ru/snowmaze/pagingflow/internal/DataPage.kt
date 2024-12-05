package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.Job
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.source.PagingSource

internal data class DataPage<Key : Any, Data : Any>(

    var data: UpdatableData<Key, Data>?,

    var itemCount: Int?,

    var isCancelled: Boolean,
    var previousPageKey: Key?,
    val currentPageKey: Key?,
    var nextPageKey: Key?,
    var pagingSourceWithIndex: Pair<PagingSource<Key, Data>, Int>,
    val listenJob: Job,
    var pageIndex: Int,
    var dataSourceIndex: Int,
    var pageIndexInPagingSource: Int
) {

    inline val currentItemCount get() = data?.data?.size ?: itemCount ?: 0

    inline val isNullified get() = itemCount == null
}
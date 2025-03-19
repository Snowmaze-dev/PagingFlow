package ru.snowmaze.pagingflow.utils

import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.internal.DataPage
import ru.snowmaze.pagingflow.source.PagingSource

class PageInfo<Key : Any, Data : Any>(
    val index: Int,
    val pageKey: Key?,
    val nextPageKey: Key?,
    val previousPageKey: Key?,
    val pagingSourceWithIndex: Pair<PagingSource<Key, Data>, Int>,
    val data: UpdatableData<Key, Data>?
)

internal inline fun <Key: Any, Data: Any> DataPage<Key, Data>.toInfo() = PageInfo(
    index = pageIndex,
    pageKey = currentPageKey,
    nextPageKey = nextPageKey,
    previousPageKey = previousPageKey,
    pagingSourceWithIndex = pagingSourceWithIndex,
    data = data
)
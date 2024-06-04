package ru.snowmaze.pagingflow.utils

import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.internal.DataPage
import ru.snowmaze.pagingflow.sources.DataSource

class PageInfo<Key : Any, Data : Any>(
    val index: Int,
    val pageKey: Key?,
    val nextPageKey: Key?,
    val previousPageKey: Key?,
    val dataSourceWithIndex: Pair<DataSource<Key, Data>, Int>,
    val data: UpdatableData<Key, Data>?
)

internal fun <Key: Any, Data: Any> DataPage<Key, Data>.toInfo() = PageInfo(
    index = pageIndex,
    pageKey = currentPageKey,
    nextPageKey = nextPageKey,
    previousPageKey = previousPageKey,
    dataSourceWithIndex = dataSourceWithIndex,
    data = dataFlow?.value
)
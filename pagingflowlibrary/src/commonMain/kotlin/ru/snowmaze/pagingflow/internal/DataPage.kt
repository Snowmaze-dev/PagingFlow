package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.sources.DataSource

internal data class DataPage<Key : Any, Data : Any, PagingStatus : Any>(
    var dataFlow: MutableStateFlow<UpdatableData<Key, Data>?>?,
    var previousPageKey: Key?,
    val currentPageKey: Key?,
    var nextPageKey: Key?,
    val dataSource: Pair<DataSource<Key, Data, PagingStatus>, Int>,
    val listenJob: Job,
    val pageIndex: Int,
    val dataSourceIndex: Int
)
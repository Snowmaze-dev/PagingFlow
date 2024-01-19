package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.sources.DataSource

internal data class DataPage<Key : Any, Data : Any, PagingStatus : Any>(
    var dataFlow: StateFlow<UpdatableData<Key, Data>>?,
    val previousPageKey: Key?,
    val currentPageKey: Key?,
    val nextPageKey: Key?,
    val pageIndex: Int,
    val dataSource: DataSource<Key, Data, PagingStatus>,
    val listenJob: Job,
    val cachedResult: PagingParams?
)
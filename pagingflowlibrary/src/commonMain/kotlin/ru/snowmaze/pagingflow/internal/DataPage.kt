package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.sources.DataSource

internal data class DataPage<Key : Any, Data : Any, PagingStatus : Any>(
    var dataFlow: StateFlow<UpdatableData<Key, Data>>?,
    val previousPageKey: Key?,
    val currentPageKey: Key?,
    val nextPageKey: Key?,
    val dataSource: DataSource<Key, Data, PagingStatus>,
    val listenJob: Job,
    var lastDataSize: Int = 0,
    val additionalData: Any? = null
)
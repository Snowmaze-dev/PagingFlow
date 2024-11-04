package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.Job
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.sources.DataSource
import kotlin.concurrent.Volatile

internal data class DataPage<Key : Any, Data : Any>(

    @Volatile
    var data: UpdatableData<Key, Data>?,

    var isNullified: Boolean,
    var previousPageKey: Key?,
    val currentPageKey: Key?,
    var nextPageKey: Key?,
    var dataSourceWithIndex: Pair<DataSource<Key, Data>, Int>,
    val listenJob: Job,
    var pageIndex: Int,
    var dataSourceIndex: Int,
    var pageIndexInDataSource: Int
)
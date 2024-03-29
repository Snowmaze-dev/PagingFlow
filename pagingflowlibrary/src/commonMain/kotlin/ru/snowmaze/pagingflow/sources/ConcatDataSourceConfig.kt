package ru.snowmaze.pagingflow.sources

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.snowmaze.pagingflow.LoadParams

class ConcatDataSourceConfig<Key : Any>(
    val defaultParamsProvider: () -> LoadParams<Key>,
    val maxPagesCount: Int? = null,
    val maxCachedResultPagesCount: Int? = null,
    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val coroutineScope: CoroutineScope = CoroutineScope(mainDispatcher + SupervisorJob()),
    val shouldFillRemovedPagesWithNulls: Boolean = true
)
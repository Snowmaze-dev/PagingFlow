package ru.snowmaze.pagingflow

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

data class PagingFlowConfiguration<Key : Any>(
    val defaultParams: LoadParams<Key>,
    val removePagesOffset: Int? = null,
    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val coroutineScope: CoroutineScope = CoroutineScope(mainDispatcher + SupervisorJob()),
    val shouldFillRemovedPagesWithNulls: Boolean = true
)
package ru.snowmaze.pagingflow

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.snowmaze.pagingflow.source.MaxItemsConfiguration

/**
 * Configuration for [PagingFlow]
 */
data class PagingFlowConfiguration<Key : Any>(

    /**
     * Defines provider of params which will be used as default params to load data from sources
     */
    val defaultParamsProvider: () -> LoadParams<Key>,

    val maxItemsConfiguration: MaxItemsConfiguration? = null,

    // defines should use stateIn to collect pages and await data set or not when collecting pages
    val shouldCollectOnlyLatest: Boolean = false,

    // Defines should store page items inside PagingFlow or not.
    // If not it wouldn't be possible to subscribe to paging changes after first page loaded or before invalidate
    val shouldStorePageItems: Boolean = true,

    val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val coroutineScope: CoroutineScope = CoroutineScope(processingDispatcher + SupervisorJob()),
) {

    constructor(
        defaultParams: LoadParams<Key>,
        maxItemsConfiguration: MaxItemsConfiguration? = null,
        processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
        coroutineScope: CoroutineScope = CoroutineScope(processingDispatcher + SupervisorJob()),
    ) : this(
        defaultParamsProvider = { defaultParams },
        maxItemsConfiguration = maxItemsConfiguration,
        processingDispatcher = processingDispatcher,
        coroutineScope = coroutineScope
    )
}
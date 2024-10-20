package ru.snowmaze.pagingflow

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.snowmaze.pagingflow.sources.MaxItemsConfiguration

/**
 * Configuration for [PagingFlow]
 */
data class PagingFlowConfiguration<Key : Any>(

    /**
     * Defines provider of params which will be used as default params to load data from sources
     */
    val defaultParamsProvider: () -> LoadParams<Key>,

    val maxItemsConfiguration: MaxItemsConfiguration? = null,

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
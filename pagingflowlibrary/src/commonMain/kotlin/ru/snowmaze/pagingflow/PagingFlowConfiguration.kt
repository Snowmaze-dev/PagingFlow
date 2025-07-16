package ru.snowmaze.pagingflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Configuration for [PagingFlow]
 */
data class PagingFlowConfiguration<Key : Any>(

    /**
     * Defines provider of params which will be used as default params to load data from sources
     */
    val defaultParamsProvider: () -> LoadParams<Key>,

    /**
     * @see MaxItemsConfiguration
     */
    val maxItemsConfiguration: MaxItemsConfiguration? = null,

    /**
     * Defines should use stateIn to collect pages and await data set before collecting next value
     * or not when collecting pages flows
     */
    val collectOnlyLatest: Boolean = false,

    /**
     * Defines should store page items inside PagingFlow or not.
     * If not it wouldn't be possible to subscribe to paging changes after any pages loaded
     * but you can invalidate paging flow to subscribe
     */
    val storePageItems: Boolean = true,

    val processingContext: CoroutineContext = Dispatchers.Default,
    val coroutineScope: CoroutineScope = CoroutineScope(processingContext + SupervisorJob()),
) {

    constructor(
        defaultParams: LoadParams<Key>,
        maxItemsConfiguration: MaxItemsConfiguration? = null,
        processingDispatcher: CoroutineContext = Dispatchers.Default,
        coroutineScope: CoroutineScope = CoroutineScope(processingDispatcher + SupervisorJob()),
    ) : this(
        defaultParamsProvider = { defaultParams },
        maxItemsConfiguration = maxItemsConfiguration,
        processingContext = processingDispatcher,
        coroutineScope = coroutineScope
    )
}

data class MaxItemsConfiguration(

    /**
     * Defines the maximum number of pages that may be loaded before pages should be dropped
     */
    val maxItemsCount: Int? = null,

    /**
     * Defines the maximum number of cached result of pages that may be reused before cache should be dropped
     */
    val maxCachedResultPagesCount: Int? = null,

    /**
     * Defines whether should replace dropped pages with nulls or just drop them completely
     */
    val enableDroppedPagesNullPlaceholders: Boolean = true
)
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

data class MaxItemsConfiguration @ExperimentalPagingApi constructor(

    /**
     * The maximum number of pages that can be loaded before older pages are automatically dropped.
     */
    val maxItemsCount: Int,

    /**
     * The maximum number of page cache results to keep in cache.
     * If null, there is no limit to the number of cached pages.
     */
    val maxCachedResultPagesCount: Int? = null,

    /**
     * Controls how dropped pages are handled when [maxItemsCount] is reached:
     * - If null: Pages are dropped completely without replacement
     * - If 0: Unlimited null placeholders can replace dropped pages
     * - If > 0: The maximum number of null placeholders allowed to replace dropped pages
     */
    val maxDroppedPagesItemsCount: Int? = 0,

    /**
     * Determines whether to restore previously dropped null pages when loading new pages in a direction
     * where null pages existed. Only takes effect when [maxDroppedPagesItemsCount] is non-null and > 0.
     */
    @property:ExperimentalPagingApi val restoreDroppedNullPagesWhenNeeded: Boolean
) {

    constructor(
        /**
         * The maximum number of pages that can be loaded before older pages are automatically dropped.
         */
        maxItemsCount: Int,

        /**
         * The maximum number of page cache results to keep in cache.
         * If null, there is no limit to the number of cached pages.
         */
        maxCachedResultPagesCount: Int? = null,

        /**
         * Controls how dropped pages are handled when [maxItemsCount] is reached:
         * - If null: Pages are dropped completely without replacement
         * - If 0: Unlimited null placeholders can replace dropped pages
         * - If > 0: The maximum number of null placeholders allowed to replace dropped pages
         */
        maxDroppedPagesItemsCount: Int? = 0,
    ) : this(
        maxItemsCount = maxItemsCount,
        maxCachedResultPagesCount = maxCachedResultPagesCount,
        maxDroppedPagesItemsCount = maxDroppedPagesItemsCount,
        restoreDroppedNullPagesWhenNeeded = false
    )

    init {
        require(maxItemsCount > 0) {
            "maxItemsCount should be more than 0"
        }

        if (maxCachedResultPagesCount != null) require(maxCachedResultPagesCount > 0) {
            "maxCachedResultPagesCount should be more than 0"
        }

        if (maxDroppedPagesItemsCount != null) require(maxDroppedPagesItemsCount >= 0) {
            "maxDroppedPagesItemsCount should be more than or equals 0"
        }
    }
}
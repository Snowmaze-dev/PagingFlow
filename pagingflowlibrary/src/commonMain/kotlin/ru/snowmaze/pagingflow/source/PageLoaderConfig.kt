package ru.snowmaze.pagingflow.source

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.snowmaze.pagingflow.LoadParams

class PageLoaderConfig<Key : Any>(
    val defaultParamsProvider: () -> LoadParams<Key>,
    val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val coroutineScope: CoroutineScope = CoroutineScope(processingDispatcher + SupervisorJob()),
    val maxItemsConfiguration: MaxItemsConfiguration? = null,

    // defines should use stateIn to collect pages and await data set or not when collecting pages
    val shouldCollectOnlyNew: Boolean = false
)

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
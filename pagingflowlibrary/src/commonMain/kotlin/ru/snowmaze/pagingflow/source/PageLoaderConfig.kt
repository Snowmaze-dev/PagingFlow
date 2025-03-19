package ru.snowmaze.pagingflow.source

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import ru.snowmaze.pagingflow.LoadParams

class PageLoaderConfig<Key : Any>(
    val defaultParamsProvider: () -> LoadParams<Key>,
    val processingDispatcher: CoroutineDispatcher,
    val coroutineScope: CoroutineScope,
    val maxItemsConfiguration: MaxItemsConfiguration?,

    // defines should use zero-sized buffer to collect pages and await data set or not when collecting pages
    val collectOnlyLatest: Boolean,

    // defines should store page items inside PagingFlow or not.
    // if not it wouldn't be possible to subscribe to paging changes after first page loaded or before invalidate
    val storePageItems: Boolean
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
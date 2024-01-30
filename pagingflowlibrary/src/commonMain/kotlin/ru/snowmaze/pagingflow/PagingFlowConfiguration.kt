package ru.snowmaze.pagingflow

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Configuration for [PagingFlow]
 */
data class PagingFlowConfiguration<Key : Any>(

    /**
     * Defines params fields from which will be used as default values to load data
     */
    val defaultParamsProvider: () -> LoadParams<Key>,

    /**
     * Defines the maximum number of pages that may be loaded before pages should be dropped
     */
    val maxPagesCount: Int? = null,

    /**
     * Defines the maximum number of cached result of pages that may be reused before cache should be dropped
     */
    val maxCachedResultPagesCount: Int? = null,

    /**
     * Defines whether should replace dropped pages with nulls or just drop them completely
     */
    val enableDroppedPagesNullPlaceholders: Boolean = true,

    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val coroutineScope: CoroutineScope = CoroutineScope(mainDispatcher + SupervisorJob()),
) {

    constructor(
        defaultParams: LoadParams<Key>,
        maxPagesCount: Int? = null,
        maxCachedResultPagesCount: Int? = null,
        enableDroppedPagesNullPlaceholders: Boolean = true,
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
        processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
        coroutineScope: CoroutineScope = CoroutineScope(mainDispatcher + SupervisorJob()),
    ) : this(
        defaultParamsProvider = { defaultParams },
        maxPagesCount = maxPagesCount,
        maxCachedResultPagesCount = maxCachedResultPagesCount,
        enableDroppedPagesNullPlaceholders = enableDroppedPagesNullPlaceholders,
        mainDispatcher = mainDispatcher,
        processingDispatcher = processingDispatcher,
        coroutineScope = coroutineScope
    )
}
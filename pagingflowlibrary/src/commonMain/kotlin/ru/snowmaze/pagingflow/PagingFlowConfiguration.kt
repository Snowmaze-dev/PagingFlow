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
    val defaultParams: LoadParams<Key>,

    /**
     * Defines the maximum number of pages that may be loaded before pages should be dropped
     */
    val maxPagesCount: Int? = null,

    /**
     * Defines whether should replace dropped pages with nulls or just drop them completely
     */
    val enableDroppedPagesNullPlaceholders: Boolean = true,

    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val coroutineScope: CoroutineScope = CoroutineScope(mainDispatcher + SupervisorJob()),
)
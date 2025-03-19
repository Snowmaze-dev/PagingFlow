package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.errorshandler.DefaultPagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.errorshandler.PagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.source.ConcatPagingSource
import ru.snowmaze.pagingflow.source.PageLoaderConfig
import ru.snowmaze.pagingflow.source.PagingSource

/**
 * Creates paging flow
 *
 * Usage:
 * ```
 * buildPagingFlow(
 *   configuration = PagingFlowConfiguration(defaultParams = LoadParams(pageSize = 100, key = 0)),
 *   loadFirstPage = false,
 * ) {
 *     addDataSource(FirstDataSource())
 *     addDataSource(SecondDataSource())
 *     addDataSource(ThirdDataSource())
 * }
 * ```
 *
 * @param configuration configuration of PagingFlow
 * @param loadFirstPage should load first page just after creating PagingFlow
 * @param builder configures PagingFlow
 */
fun <Key : Any, Data : Any> buildPagingFlow(
    configuration: PagingFlowConfiguration<Key>,
    pagingUnhandledErrorsHandler: PagingUnhandledErrorsHandler<Key, Data> = DefaultPagingUnhandledErrorsHandler(),
    loadFirstPage: Boolean = false,
    builder: PagingFlow<Key, Data>.() -> Unit = {}
) = PagingFlow<Key, Data>(
    ConcatPagingSource(
        PageLoaderConfig(
            defaultParamsProvider = configuration.defaultParamsProvider,
            maxItemsConfiguration = configuration.maxItemsConfiguration,
            processingDispatcher = configuration.processingDispatcher,
            coroutineScope = configuration.coroutineScope,
            storePageItems = configuration.storePageItems,
            collectOnlyLatest = configuration.collectOnlyLatest
        ),
        pagingUnhandledErrorsHandler = pagingUnhandledErrorsHandler
    ),
    configuration,
).apply {
    apply(builder)
    if (loadFirstPage) loadNextPage()
}

/**
 * Creates paging flow
 *
 * Usage:
 * ```
 * buildPagingFlow(
 *   configuration = PagingFlowConfiguration(defaultParams = LoadParams(pageSize = 100, key = 0)),
 *   loadFirstPage = false,
 *   FirstDataSource(),
 *   SecondDataSource(),
 *   ThirdDataSource(),
 *   ...
 * )
 * ```
 *
 * @param configuration configuration of PagingFlow
 * @param loadFirstPage should load first page just after creating PagingFlow
 * @param pagingSources paging sources list to be added to PagingFlow
 */
fun <Key : Any, Data : Any> buildPagingFlow(
    configuration: PagingFlowConfiguration<Key>,
    loadFirstPage: Boolean,
    vararg pagingSources: PagingSource<Key, out Data>,
    pagingUnhandledErrorsHandler: PagingUnhandledErrorsHandler<Key, Data> = DefaultPagingUnhandledErrorsHandler(),
) = buildPagingFlow(
    configuration = configuration,
    loadFirstPage = loadFirstPage,
    pagingUnhandledErrorsHandler = pagingUnhandledErrorsHandler
) {
    for (pagingSource in pagingSources) {
        addDownPagingSource(pagingSource)
    }
}

fun <Key : Any, Data : Any> buildPagingFlow(
    configuration: PagingFlowConfiguration<Key>,
    vararg pagingSources: PagingSource<Key, out Data>,
    pagingUnhandledErrorsHandler: PagingUnhandledErrorsHandler<Key, Data> = DefaultPagingUnhandledErrorsHandler(),
) = buildPagingFlow(
    configuration = configuration,
    pagingUnhandledErrorsHandler = pagingUnhandledErrorsHandler
) {
    for (pagingSource in pagingSources) {
        addDownPagingSource(pagingSource)
    }
}
package ru.snowmaze.pagingflow

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.sources.ConcatDataSource
import ru.snowmaze.pagingflow.sources.ConcatDataSourceConfig
import ru.snowmaze.pagingflow.sources.ConcatSourceData
import ru.snowmaze.pagingflow.sources.DataSource

// так и с маппингом в уже готовые output данные
// сделать возможность маппить данные внутри
// TODO
class PagingFlow<Key : Any, Data : Any, PagingStatus : Any>(
    private val concatDataSource: SourcesChainSource<Key, Data, PagingStatus>,
    private val pagingFlowConfiguration: PagingFlowConfiguration<Key>
) {

    private val loadMutex = Mutex()
    val pagingStatus get() = concatDataSource.pagingStatus
    val isLoading get() = loadMutex.isLocked

    val currentPagesCount get() = concatDataSource.currentPagesCount
    val dataFlow: StateFlow<List<Data?>> = concatDataSource.dataFlow
    private val defaultParams = pagingFlowConfiguration.defaultParams

    fun addDataSource(dataSource: DataSource<Key, Data, PagingStatus>) {
        concatDataSource.addDataSource(dataSource)
    }

    fun removeDataSource(dataSource: DataSource<Key, Data, PagingStatus>) {
        concatDataSource.removeDataSource(dataSource)
    }

    fun loadNextPage(
        paginationDirection: PaginationDirection = defaultParams.paginationDirection
    ) = pagingFlowConfiguration.coroutineScope.launch {
        loadNextPageWithResult(paginationDirection)
    }

    suspend fun loadNextPageWithResult(
        paginationDirection: PaginationDirection = defaultParams.paginationDirection
    ) = loadMutex.withLock {
        pagingFlowConfiguration.processingDispatcher {
            loadNextPageInternal(paginationDirection)
        }
    }

    fun trimPages() {

    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun loadNextPageInternal(
        paginationDirection: PaginationDirection
    ): LoadNextPageResult<Key, Data> {

        // TODO сделать поддержку передачи ключей пагинации тут nextPageKey
        val loadData = concatDataSource.loadData(
            pagingFlowConfiguration.defaultParams.copy(
                paginationDirection = paginationDirection
            )
        )
        val additionalData = loadData.additionalData as? ConcatSourceData<Key>
        return when (loadData) {
            is LoadResult.Success<Key, Data, PagingStatus> -> LoadNextPageResult.Success(
                currentKey = additionalData?.currentKey,
                dataFlow = loadData.dataFlow,
                hasNext = loadData.nextNextPageKey != null,
                additionalData = if (additionalData == null) additionalData
                else additionalData.additionalData
            )

            is LoadResult.Failure<*, *, PagingStatus> -> LoadNextPageResult.Failure(
                additionalData = additionalData,
                exception = loadData.exception
            )
        }
    }

    suspend fun invalidate() = concatDataSource.invalidate()
}

fun <Key : Any, Data : Any, PagingStatus : Any> buildPagingFlow(
    configuration: PagingFlowConfiguration<Key>,
    builder: PagingFlow<Key, Data, PagingStatus>.() -> Unit
) = PagingFlow<Key, Data, PagingStatus>(
    ConcatDataSource(
        ConcatDataSourceConfig(
            defaultParams = configuration.defaultParams,
            removePagesOffset = configuration.removePagesOffset,
            mainDispatcher = configuration.mainDispatcher,
            processingDispatcher = configuration.processingDispatcher,
            coroutineScope = configuration.coroutineScope,
            shouldFillRemovedPagesWithNulls = configuration.shouldFillRemovedPagesWithNulls
        )
    ), configuration
).apply(builder)
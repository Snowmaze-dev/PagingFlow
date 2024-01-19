package ru.snowmaze.pagingflow.sources

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.LoadResult
import ru.snowmaze.pagingflow.MutablePagingValue
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.SourcesChainSource
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.internal.DataPage
import ru.snowmaze.pagingflow.internal.DataSources
import ru.snowmaze.pagingflow.simpleResult

class ConcatDataSource<Key : Any, Data : Any, PagingStatus : Any>(
    private val concatDataSourceConfig: ConcatDataSourceConfig<Key>
) : SourcesChainSource<Key, Data, PagingStatus> {

    private val defaultParams = concatDataSourceConfig.defaultParams
    private val setDataMutex = Mutex()
    private val _pagingStatus = MutableStateFlow<PagingStatus?>(null)
    override val pagingStatus get() = _pagingStatus.asStateFlow()
    override val isLoading get() = setDataMutex.isLocked

    private val dataSources = DataSources<Key, Data, PagingStatus>()
    private val dataPages = mutableListOf<DataPage<Key, Data, PagingStatus>>()
    private val _dataFlow = MutableStateFlow(listOf<Data?>())
    override val currentPagesCount get() = dataPages.size
    override val dataFlow = _dataFlow.asStateFlow()
    private var isNeedToTrim = false
    private var lastPaginationDirection: PaginationDirection? = null
    private val coroutineScope = concatDataSourceConfig.coroutineScope

    override fun addDataSource(dataSource: DataSource<Key, Data, PagingStatus>) {
        dataSources.addDataSource(dataSource)
    }

    override fun removeDataSource(dataSource: DataSource<Key, Data, PagingStatus>) {
        dataSources.removeDataSource(dataSource)
        coroutineScope.launch {
            invalidate()
        }
    }

    // TODO сделать поддерку передачи ключей через loadParams
    override suspend fun loadData(loadParams: LoadParams<Key>): LoadResult<Key, Data, PagingStatus> {
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        val paginationDirection = loadParams.paginationDirection
        val lastPageIndex = if (isPaginationDown) dataPages.indexOfLast { it.dataFlow != null }
        else dataPages.indexOfFirst { it.dataFlow != null }
        val lastPage = dataPages.getOrNull(lastPageIndex)
        val nextPageKey = if (isPaginationDown) lastPage?.nextPageKey else lastPage?.previousPageKey
        val dataSource = dataSources.findNextDataSource(
            currentDataSource = lastPage?.dataSource,
            isThereKey = nextPageKey != null,
            navigationDirection = paginationDirection
        ) ?: return simpleResult(emptyList())
        val currentKey = nextPageKey ?: if (dataPages.isEmpty()) {
            dataSource.defaultLoadParams?.key ?: loadParams.key ?: defaultParams.key
        } else null
        val newIndex = if (isPaginationDown) lastPageIndex + 1
        else lastPageIndex - 1
        val nextLoadParams = LoadParams(
            loadSize = dataSource.defaultLoadParams?.loadSize ?: loadParams.loadSize,
            paginationDirection = paginationDirection,
            key = currentKey,
            existingAdditionalData = dataPages.getOrNull(newIndex)?.additionalData
                ?: loadParams.existingAdditionalData ?: defaultLoadParams?.existingAdditionalData
        )
        val result = try {
            dataSource.loadData(nextLoadParams)
        } catch (e: Exception) {
            val errorHandler = (dataSource.pagingErrorsHandler ?: pagingErrorsHandler)
            errorHandler?.handle(e) ?: throw e
        }
        if (result is LoadResult.Failure) return result as LoadResult<Key, Data, PagingStatus>
        result as LoadResult.Success<Key, Data, PagingStatus>
        _pagingStatus.value = result.status
        if (concatDataSourceConfig.removePagesOffset != null) {
            isNeedToTrim = true
            lastPaginationDirection = paginationDirection
        }
        val valueStateFlow = MutableStateFlow(
            UpdatableData<Key, Data>(
                data = emptyList(),
                nextPageKey = result.nextNextPageKey
            )
        )
        val listenJob = SupervisorJob()
        val page = DataPage(
            dataFlow = valueStateFlow,
            nextPageKey = if (isPaginationDown) result.nextNextPageKey else dataPages.first().currentPageKey,
            dataSource = dataSource,
            previousPageKey = if (isPaginationDown) dataPages.lastOrNull()?.currentPageKey
            else result.nextNextPageKey,
            currentPageKey = currentKey,
            listenJob = listenJob,
            additionalData = result.additionalData
        )
        setDataMutex.withLock {

            val isExistingPage = dataPages.getOrNull(newIndex) != null
            if (isExistingPage) {
                dataPages[newIndex] = page
            } else {
                if (isPaginationDown) dataPages.add(page)
                else dataPages.add(0, page)
            }
        }

        coroutineScope {
            launch(concatDataSourceConfig.mainDispatcher + listenJob) {
                result.dataFlow?.collect {
                    valueStateFlow.value = it
                    updateData()
                }
            }
        }
        return result.copy(
            dataFlow = valueStateFlow,
            additionalData = ConcatSourceData(currentKey, result.additionalData)
        )
    }

    override suspend fun invalidate() = setDataMutex.withLock {
        for (page in dataPages) {
            page.listenJob.cancel()
        }
        dataPages.clear()
        _dataFlow.value = (emptyList())
    }

    private fun trimPages() {
        if (!isNeedToTrim) return
        isNeedToTrim = false
        val lastPaginationDirection = lastPaginationDirection ?: return
        val removePagesOffset = concatDataSourceConfig
            .removePagesOffset.takeIf { it != 0 } ?: return
        if (dataPages.size > removePagesOffset) {
            val isDown = lastPaginationDirection == PaginationDirection.DOWN

            // заменить на удаляемую страницу
            val pageIndex = (if (isDown) 0
            else dataPages.lastIndex)
            val page = dataPages.getOrNull(pageIndex) ?: return
            if (concatDataSourceConfig.shouldFillRemovedPagesWithNulls && isDown) {
                page.lastDataSize = page.dataFlow?.value?.data?.size ?: return
                page.listenJob.cancel()
                page.dataFlow = null
            } else {
                page.listenJob.cancel()
                dataPages.removeAt(pageIndex)
            }
        }
    }

    // Вынести страницы в PagingFlow
    private suspend fun updateData() {
        setDataMutex.withLock {
            _dataFlow.value = concatDataSourceConfig.processingDispatcher {
                trimPages()
                buildList(dataPages.sumOf {
                    it.dataFlow?.value?.data?.size ?: it.lastDataSize
                }) {
                    for (page in dataPages) {
                        val data = page.dataFlow?.value?.data
                        if (data == null) {
                            if (concatDataSourceConfig.shouldFillRemovedPagesWithNulls) addAll(
                                buildList(page.lastDataSize) {
                                    repeat(page.lastDataSize) {
                                        add(null)
                                    }
                                }
                            )
                        } else addAll(data)
                    }
                }
            }
        }
    }
}
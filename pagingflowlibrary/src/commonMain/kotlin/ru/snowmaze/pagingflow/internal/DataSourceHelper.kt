package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.sources.DataSource
import ru.snowmaze.pagingflow.utils.DiffOperation

internal class DataSourceHelper<Key : Any, Data : Any>(
    private val dataSourcesManager: DataSourcesManager<Key, Data>,
    private val dataPagesManager: DataPagesManager<Key, Data>,
    private val pageLoader: PageLoader<Key, Data>,
    private val loadDataMutex: Mutex
) {

    private val pagesCount get() = dataPagesManager.dataPages.count { it.data != null }

    suspend fun setDataSources(
        newDataSourceList: List<DataSource<Key, Data>>,
        diff: (
            oldList: List<DataSource<Key, Data>>,
            newList: List<DataSource<Key, Data>>
        ) -> List<DiffOperation<DataSource<Key, Data>>>
    ) = loadDataMutex.withLock {
        val dataSources = dataSourcesManager.dataSources
        val operations = diff(dataSources, newDataSourceList)
        if (operations.isEmpty()) return@withLock
        for (operation in operations) {
            when (operation) {
                is DiffOperation.Remove<*> -> repeat(operation.count) { remove(operation.index) }

                is DiffOperation.Add<DataSource<Key, Data>> -> {
                    for (item in (operation.items ?: continue).withIndex()) {
                        insert(item.value, operation.index + item.index)
                    }
                }

                is DiffOperation.Move -> move(
                    oldIndex = operation.fromIndex,
                    newIndex = operation.toIndex
                )
            }
        }
        dataPagesManager.resendAllPages()
    }

    fun remove(index: Int) {
        if (dataSourcesManager.removeDataSource(index)) {
            dataPagesManager.removeDataSourcePages(index)
        }
    }

    private suspend fun insert(item: DataSource<Key, Data>, index: Int) {
        val lastLoadedDataSource = dataPagesManager.dataPages.maxOfOrNull { it.dataSourceIndex }
        dataSourcesManager.addDataSource(item, index)
        if (index > (lastLoadedDataSource ?: 0) || pagesCount == 0) return
        val previousIndex = (index - 1).coerceAtLeast(0)
        var pageIndex = if (index == 0) -1 else dataPagesManager.dataPages.indexOfLast {
            it.dataSourceIndex == previousIndex
        }
        do {
            val result = pageLoader.loadData(
                loadParams = pageLoader.pageLoaderConfig.defaultParamsProvider(),
                lastPageIndex = pageIndex,
                shouldReplaceOnConflict = false
            )
            pageIndex++
        } while (result is LoadResult.Success<Key, Data> && result.nextPageKey != null)
    }

    private fun move(oldIndex: Int, newIndex: Int) {
        val newMaxIndex = newIndex
            .coerceAtMost(dataSourcesManager.dataSources.size - 1)
            .coerceAtLeast(0)
        try {
            dataSourcesManager.moveDataSource(oldIndex, newMaxIndex)
            dataPagesManager.movePages(oldIndex, newMaxIndex)
        } catch (e: Exception) { }
    }
}
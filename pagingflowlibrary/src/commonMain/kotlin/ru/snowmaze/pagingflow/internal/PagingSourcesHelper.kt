package ru.snowmaze.pagingflow.internal

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.source.PagingSource
import ru.snowmaze.pagingflow.utils.DiffOperation
import kotlin.coroutines.CoroutineContext

internal class PagingSourcesHelper<Key : Any, Data : Any>(
    private val pagingSourcesManager: PagingSourcesManager<Key, Data>,
    private val dataPagesManager: DataPagesManager<Key, Data>,
    private val pageLoader: PageLoader<Key, Data>,
    private val loadDataMutex: Mutex,
    private val processingContext: CoroutineContext,
) {

    private val pagesCount get() = dataPagesManager.dataPages.count { !it.isNullified }

    suspend fun setPagingSources(
        newPagingSourceList: List<PagingSource<Key, out Data>>,
        diff: (
            oldList: List<PagingSource<Key, out Data>>,
            newList: List<PagingSource<Key, out Data>>
        ) -> List<DiffOperation<PagingSource<Key, out Data>>>
    ) = loadDataMutex.withLock {
        withContext(processingContext + NonCancellable) {
            val dataSources = pagingSourcesManager.downPagingSources
            val operations = diff(dataSources.asList(), newPagingSourceList)
            if (operations.isEmpty()) return@withContext
            for (operation in operations) {
                when (operation) {
                    is DiffOperation.Remove<*> -> {
                        repeat(operation.count) { remove(operation.index) }
                    }

                    is DiffOperation.Add<PagingSource<Key, out Data>> -> {
                        for (item in (operation.items ?: continue).withIndex()) {
                            insert(
                                item.value as PagingSource<Key, Data>, operation.index + item.index
                            )
                            dataPagesManager.updateIndexes()
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
    }

    fun remove(index: Int) {
        if (pagingSourcesManager.removePagingSource(index)) {
            dataPagesManager.removePagingSourcePages(index)
        }
    }

    private suspend fun insert(item: PagingSource<Key, Data>, index: Int) {
        val lastLoadedDataSource = dataPagesManager.dataPagesList.maxOfOrNull { it.dataSourceIndex }
        pagingSourcesManager.addDownPagingSource(item, index)
        if (index > (lastLoadedDataSource ?: 0) || pagesCount == 0) return
        val previousIndex = (index - 1).coerceAtLeast(0)
        var pageIndex = if (index == 0) -1 else dataPagesManager.dataPages.indexOfLast {
            it.dataSourceIndex == previousIndex
        }
        do {
            val result = pageLoader.loadData(
                loadParams = pageLoader.pageLoaderConfig.defaultParamsProvider(),
                lastPageIndex = pageIndex,
                shouldReplaceOnConflict = false,
                shouldSetNewStatus = true
            )
            pageIndex++
        } while (result is LoadResult.Success<Key, Data> && result.nextPageKey != null)
    }

    private fun move(oldIndex: Int, newIndex: Int) {
        val newMaxIndex = newIndex.coerceAtLeast(0)
        try {
            pagingSourcesManager.movePagingSource(oldIndex, newMaxIndex)
            dataPagesManager.movePages(oldIndex, newMaxIndex)
        } catch (e: Exception) { }
    }
}
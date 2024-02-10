package ru.snowmaze.pagingflow

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import ru.snowmaze.pagingflow.presenters.PagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import kotlin.test.assertEquals

val testDispatcher = UnconfinedTestDispatcher()

suspend fun PagingFlow<Int, String, DefaultPagingStatus>.testLoadEverything(
    dataSources: List<TestDataSource>,
    pageSize: Int,
    shouldTestItems: Boolean = true,
    pagingPresenter: PagingDataPresenter<Int, String>? = null
) {
    var dataSourceIndex = 0
    var currentDataSource = dataSources[dataSourceIndex]
    var currentSourceLoadedCount = pagingPresenter?.dataFlow?.value?.size ?: 0
    var currentLoadSize = currentDataSource.defaultLoadParams?.pageSize ?: pageSize
    var currentTotalCount = currentDataSource.totalCount
    val loadingSources = mutableListOf(currentDataSource)
    var overallLoadedCount = pagingPresenter?.dataFlow?.value?.size ?: 0

    while (true) {
        val result = loadNextPageWithResult()
        currentSourceLoadedCount += currentLoadSize
        overallLoadedCount += currentLoadSize
        currentLoadSize = (currentTotalCount - currentSourceLoadedCount).coerceAtMost(pageSize)
        if (shouldTestItems) {
            assertEquals(currentTotalCount != currentSourceLoadedCount, result.asSuccess().hasNext)
            var testItems: List<String?> = loadingSources.mapIndexed { index, testDataSource ->
                testDataSource.getItems(
                    if (index == loadingSources.lastIndex) {
                        currentSourceLoadedCount
                    } else testDataSource.totalCount
                )
            }.flatten()
            val maxPagesCount = pagingFlowConfiguration.maxPagesCount
            if (maxPagesCount != null) {
                val maxItemsOffset = maxPagesCount * pageSize
                val removeItemsCount = (overallLoadedCount - maxItemsOffset).coerceAtLeast(0)
                testItems = if (pagingFlowConfiguration.enableDroppedPagesNullPlaceholders) {
                    testItems.mapIndexed { index, item ->
                        if (removeItemsCount > index) null
                        else item
                    }
                } else testItems.drop(removeItemsCount)
            }
            assertEquals(
                testItems,
                pagingPresenter?.dataFlow?.value
            )
        }
        if (currentLoadSize == 0) {
            currentDataSource = dataSources.getOrNull(++dataSourceIndex) ?: break
            currentSourceLoadedCount = 0
            currentTotalCount = currentDataSource.totalCount
            currentLoadSize = currentDataSource.defaultLoadParams?.pageSize ?: pageSize
            loadingSources += currentDataSource
        }
    }
}

fun <Key : Any, Data : Any> LoadNextPageResult<Key, Data>.asSuccess() =
    this as LoadNextPageResult.Success<Key, Data>
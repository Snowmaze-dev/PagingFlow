package ru.snowmaze.pagingflow

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import ru.snowmaze.pagingflow.presenters.PagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import kotlin.math.ceil
import kotlin.test.assertEquals

val testDispatcher = UnconfinedTestDispatcher()

suspend fun PagingFlow<Int, String, DefaultPagingStatus>.testLoadEverything(
    dataSources: List<TestDataSource>,
    shouldTestItems: Boolean = true,
    pagingPresenter: PagingDataPresenter<Int, String>
) {
    val pageSize = pagingFlowConfiguration.defaultParamsProvider().pageSize
    var dataSourceIndex = 0
    var currentDataSource = dataSources[dataSourceIndex]
    var currentSourceLoadedCount = pagingPresenter.dataFlow.value.size
    var currentLoadSize = currentDataSource.defaultLoadParams?.pageSize ?: pageSize
    var currentTotalCount = currentDataSource.totalCount
    val loadingSources = mutableListOf(currentDataSource)
    var overallLoadedCount = pagingPresenter.dataFlow.value.size

    while (true) {
        val result = loadNextPageWithResult()
        currentSourceLoadedCount += currentLoadSize
        overallLoadedCount += currentLoadSize
        assertEquals(currentTotalCount != currentSourceLoadedCount, result.asSuccess().hasNext)
        currentLoadSize = (currentTotalCount - currentSourceLoadedCount).coerceAtMost(pageSize)
        if (shouldTestItems) {
            var testItems: List<String?> = loadingSources.mapIndexed { index, testDataSource ->
                testDataSource.getItems(
                    if (index == loadingSources.lastIndex) {
                        currentSourceLoadedCount
                    } else testDataSource.totalCount
                )
            }.flatten()
            val maxItemsOffset = pagingFlowConfiguration.maxItemsCount
            if (maxItemsOffset != null) {
                val removeItemsCount = (ceil((overallLoadedCount - maxItemsOffset)
                    .coerceAtLeast(0) / pageSize.toDouble()) * pageSize).toInt()
                testItems = if (pagingFlowConfiguration.enableDroppedPagesNullPlaceholders) {
                    testItems.mapIndexed { index, item ->
                        if (removeItemsCount > index) null
                        else item
                    }
                } else {
                    testItems.drop(removeItemsCount)
                }
            }
            assertEquals(
                testItems,
                pagingPresenter.dataFlow.value
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
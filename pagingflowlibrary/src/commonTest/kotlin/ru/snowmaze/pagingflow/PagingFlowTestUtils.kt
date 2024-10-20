package ru.snowmaze.pagingflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.invoke
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import ru.snowmaze.pagingflow.presenters.PagingDataPresenter
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.sources.TestDataSource
import kotlin.math.ceil
import kotlin.test.assertEquals

val testDispatcher = UnconfinedTestDispatcher()

suspend fun PagingFlow<Int, String>.testLoadEverything(
    dataSources: List<TestDataSource>,
    shouldTestItems: Boolean = true,
    pagingPresenter: PagingDataPresenter<Int, String>
) {
    val pageSize = pagingFlowConfiguration.defaultParamsProvider().pageSize
    var dataSourceIndex = 0
    var currentDataSource = dataSources[dataSourceIndex]
    var currentSourceLoadedCount = pagingPresenter.data.size
    var currentLoadSize = currentDataSource.defaultLoadParams?.pageSize ?: pageSize
    var currentTotalCount = currentDataSource.totalCount
    val loadingSources = mutableListOf(currentDataSource)
    var overallLoadedCount = pagingPresenter.data.size
    val overallTotalCountOfItems = dataSources.sumOf { it.totalCount }

    while (true) {
        val result = loadNextPageWithResult()
        currentSourceLoadedCount += currentLoadSize
        overallLoadedCount += currentLoadSize
        assertEquals(overallLoadedCount != overallTotalCountOfItems, result.asSuccess().hasNext)
        currentLoadSize = (currentTotalCount - currentSourceLoadedCount).coerceAtMost(pageSize)
        if (shouldTestItems) {
            var testItems: List<String?> = loadingSources.mapIndexed { index, testDataSource ->
                testDataSource.getItems(
                    if (index == loadingSources.lastIndex) {
                        currentSourceLoadedCount
                    } else testDataSource.totalCount
                )
            }.flatten()
            val maxItemsConfiguration = pagingFlowConfiguration.maxItemsConfiguration
            val maxItemsOffset = maxItemsConfiguration?.maxItemsCount
            if (maxItemsOffset != null) {
                val removeItemsCount = (ceil((overallLoadedCount - maxItemsOffset)
                    .coerceAtLeast(0) / pageSize.toDouble()) * pageSize).toInt()
                testItems = if (maxItemsConfiguration.enableDroppedPagesNullPlaceholders) {
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
                pagingPresenter.data
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

inline fun runTestOnDispatchersDefault(
    noinline block: suspend CoroutineScope.() -> Unit
) = runTest {
    Dispatchers.Default.invoke(block)
}

suspend fun <T> Flow<T>.firstWithTimeout(timeout: Long = 5000, predicate: suspend (T) -> Boolean) {
    withTimeout(timeout) { first(predicate) }
}
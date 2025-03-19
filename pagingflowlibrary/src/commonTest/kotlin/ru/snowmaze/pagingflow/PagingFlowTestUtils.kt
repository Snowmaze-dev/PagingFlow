package ru.snowmaze.pagingflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.invoke
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import ru.snowmaze.pagingflow.presenters.PagingDataPresenter
import ru.snowmaze.pagingflow.presenters.StatePagingDataPresenter
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.math.ceil
import kotlin.test.assertEquals

val testDispatcher = UnconfinedTestDispatcher()

suspend fun PagingFlow<Int, String>.testLoadEverything(
    dataSources: List<TestPagingSource>,
    shouldTestItems: Boolean = true,
    pagingPresenter: StatePagingDataPresenter<Int, String>
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
        assertEquals(result.asSuccess().hasNext, downPagingStatus.value.hasNextPage)

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
                val removeItemsCount = (ceil(
                    (overallLoadedCount - maxItemsOffset)
                        .coerceAtLeast(0) / pageSize.toDouble()
                ) * pageSize).toInt()
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

fun <Key : Any> LoadNextPageResult<Key>.asSuccess() = this as LoadNextPageResult.Success<Key>

inline fun runTestOnDispatchersDefault(
    noinline block: suspend CoroutineScope.() -> Unit
) = runTest {
    Dispatchers.Default.invoke(block)
}

suspend inline fun <T> Flow<T>.firstEqualsWithTimeout(
    value: T,
    timeout: Long = 1000
) = firstWithTimeout(timeout, {
    "expected $value but was $it"
}) {
    value == it
}

suspend fun <T> Flow<T>.firstWithTimeout(
    timeout: Long = 1000,
    message: ((T?) -> String)? = null,
    predicate: suspend (T) -> Boolean
): T {
    val cause = Exception()
    return if (message == null) withTimeout(timeout) { first(predicate) }
    else {
        var lastValue: T? = null
        val result = withTimeoutOrNull(timeout) {
            first {
                lastValue = it
                predicate(it)
            }
        }
        if (result == null) throw AssertionError(message(lastValue), cause)
        result
    }
}
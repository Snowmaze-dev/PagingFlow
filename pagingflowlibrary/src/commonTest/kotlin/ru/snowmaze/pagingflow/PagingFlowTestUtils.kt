package ru.snowmaze.pagingflow

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import ru.snowmaze.pagingflow.presenters.PagingDataPresenter
import kotlin.test.assertEquals

val testDispatcher = UnconfinedTestDispatcher()

suspend fun PagingFlow<Int, String, DefaultPagingStatus>.testLoadEverything(
    dataSources: List<TestDataSource>,
    pageSize: Int,
    shouldTestItems: Boolean = true,
    pagingPresenter: PagingDataPresenter<Int, String>
) {
    var dataSourceIndex = 0
    var currentDataSource = dataSources[dataSourceIndex]
    var currentSourceLoadedCount = 0
    var currentLoadSize = currentDataSource.defaultLoadParams?.pageSize ?: pageSize
    var currentTotalCount = currentDataSource.totalCount
    val loadingSources = mutableListOf(currentDataSource)

    while (true) {
        val result = loadNextPageWithResult()
        currentSourceLoadedCount += currentLoadSize
        assertEquals(currentTotalCount != currentSourceLoadedCount, result.asSuccess().hasNext)
        currentLoadSize = (currentTotalCount - currentSourceLoadedCount).coerceAtMost(pageSize)
        if (shouldTestItems) assertEquals(
            loadingSources.mapIndexed { index, testDataSource ->
                testDataSource.getItems(
                    if (index == loadingSources.lastIndex) {
                        currentSourceLoadedCount
                    } else testDataSource.totalCount
                )
            }.flatten(),
            pagingPresenter.dataFlow.value
        )
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
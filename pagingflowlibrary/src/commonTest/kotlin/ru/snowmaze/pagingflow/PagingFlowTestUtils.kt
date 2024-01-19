package ru.snowmaze.pagingflow

import kotlin.test.assertEquals

suspend fun PagingFlow<Int, String, DefaultPagingStatus>.testLoadEverything(
    dataSources: List<TestDataSource>,
    loadSize: Int,
    shouldTestItems: Boolean = true
) {
    var dataSourceIndex = 0
    var currentDataSource = dataSources[dataSourceIndex]
    var currentSourceLoadedCount = 0
    var currentLoadSize = currentDataSource.defaultLoadParams?.loadSize ?: loadSize
    var currentTotalCount = currentDataSource.totalCount
    val loadingSources = mutableListOf(currentDataSource)

    while (true) {
        val result = loadNextPageWithResult()
        currentSourceLoadedCount += currentLoadSize
        assertEquals(currentTotalCount != currentSourceLoadedCount, result.asSuccess().hasNext)
        currentLoadSize = (currentTotalCount - currentSourceLoadedCount).coerceAtMost(loadSize)
        if (shouldTestItems) assertEquals(
            loadingSources.mapIndexed { index, testDataSource ->
                testDataSource.getItems(
                    if (index == loadingSources.lastIndex) {
                        currentSourceLoadedCount
                    } else testDataSource.totalCount
                )
            }.flatten(),
            dataFlow.value
        )
        if (currentLoadSize == 0) {
            currentDataSource = dataSources.getOrNull(++dataSourceIndex) ?: break
            currentSourceLoadedCount = 0
            currentTotalCount = currentDataSource.totalCount
            currentLoadSize = currentDataSource.defaultLoadParams?.loadSize ?: loadSize
            loadingSources += currentDataSource
        }
    }
}

fun <Key : Any, Data : Any> LoadNextPageResult<Key, Data>.asSuccess() =
    this as LoadNextPageResult.Success<Key, Data>
# PagingFlow

### A library that enables multiplatform pagination over multiple paging sources and allows for the implementation of infinite scrolling.
Its also provides the capabilities for custom argument passing and setting custom statuses, as well as various options for transforming data pages into full-fledged lists.

# Usage

### PagingSource
```kotlin
class ExamplePagingSource(
    private val networkSource: NetworkSource,
    private val databaseSource: DatabaseSource
) : PagingSource<Int, String> {

    override suspend fun load(loadParams: LoadParams<Int>): LoadResult<Int, String> {
        val offset = loadParams.key ?: 0
        val response = try {
            networkSource.getData(offset)
        } catch (exception: Exception) {
            return LoadResult.Failure(exception = exception)
        }

        return result(
            dataFlow = databaseSource.getUsersFlow(response.userIds),
            nextPageKey = positiveOffset(
                paginationDirection = loadParams.paginationDirection,
                currentOffset = offset,
                pageSize = response.userIds.size,
                hasNextPage = response.hasNext
            )
        )
    }
}
```

### Paging flow
```kotlin
val pagingFlow = buildPagingFlow(
    PagingFlowConfiguration(
        LoadParams(pageSize = 50),
        maxPagesCount = 3
    )
) {
    addPagingSource(ExamplePagingSource(networkSource, databaseSource))
    loadNextPage()
}
val pagingDataPresenter = pagingFlow.pagingDataPresenter()
```

### Submitting data in android
```kotlin
viewLifecycleOwner.coroutineScope.launch {
   viewModel.pagingDataPresenter.dataFlow.collect { dataList ->
       adapter.submit(dataList)
   }
}
```

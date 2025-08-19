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

### Architecture

[<img width="3881" height="1080" alt="PagingFlow drawio" src="https://github.com/user-attachments/assets/dd54e981-635f-4985-9ad2-aec059897323" />](https://viewer.diagrams.net/?tags=%7B%7D&lightbox=1&highlight=0000ff&edit=_blank&layers=1&nav=1&title=PagingFlow.drawio&dark=auto#Uhttps%3A%2F%2Fdrive.google.com%2Fuc%3Fid%3D1D6pVE357gAQaY_nuGQvC8gNb2GdWj03z%26export%3Ddownload)

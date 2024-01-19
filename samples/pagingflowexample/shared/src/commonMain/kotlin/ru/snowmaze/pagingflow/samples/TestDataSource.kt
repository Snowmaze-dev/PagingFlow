package ru.snowmaze.pagingflow.samples

import ru.snowmaze.pagingflow.DefaultPagingStatus
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.simpleResult
import ru.snowmaze.pagingflow.sources.SegmentedIntDataSource

class TestDataSource(
    override val totalCount: Int,
    val withDelay: Boolean = false
) : SegmentedIntDataSource<String, DefaultPagingStatus>() {

    private val items = buildList {
        repeat(totalCount) {
            add("Item $it")
        }
    }

    override suspend fun loadData(
        loadParams: LoadParams<Int>,
        startIndex: Int,
        endIndex: Int
    ) = simpleResult(items.subList(startIndex, endIndex))

//    override suspend fun loadData(
//        loadParams: LoadParams<Int>,
//        startIndex: Int,
//        endIndex: Int
//    ): LoadResult<Int, String, DefaultPagingStatus> = if (withDelay) result(
//        dataFlow = MutableStateFlow<UpdatableData<Int, String>>(
//            UpdatableData(listOf("353231", "35323"))
//        ).also { flow ->
//            coroutineScope {
//                launch {
//                    delay(3000L)
//                    flow.value = UpdatableData(
//                        items.subList(
//                            startIndex,
//                            endIndex
//                        )
//                    )
//                }
//            }
//        }) else simpleResult(
//        items.subList(
//            startIndex,
//            endIndex
//        )
//    )
}
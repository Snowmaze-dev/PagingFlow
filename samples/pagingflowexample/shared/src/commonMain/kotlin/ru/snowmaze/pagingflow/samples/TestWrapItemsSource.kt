package ru.snowmaze.pagingflow.samples

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import ru.snowmaze.pagingflow.DefaultPagingStatus
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.LoadResult
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.sources.DataSource

//class TestWrapItemsSource<Key : Any, Data : Any>(
//    private val items: List<Data>,
//    private val dataSource: DataSource<Key, Data, DefaultPagingStatus>
//) : DataSource<Key, Data, DefaultPagingStatus> {
//
//    override suspend fun loadData(loadParams: LoadParams<Key>): LoadResult<Key, Data, DefaultPagingStatus> {
//        val result = dataSource.loadData(loadParams)
//        return result.copy(
//            dataFlow = combine(flow {
//                emit(UpdatableData(items, result.initialNextPageKey))
//            }, result.dataFlow) { first, second ->
//                println("MyActivity $first $second")
//                if (second.data.isEmpty()) first
//                else second
//            }
//        )
//    }
//}
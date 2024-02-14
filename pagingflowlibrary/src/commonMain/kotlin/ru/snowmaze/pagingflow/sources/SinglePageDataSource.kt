package ru.snowmaze.pagingflow.sources

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.result.LoadResult

class SinglePageDataSource<Key : Any, Data : Any, PagingStatus : Any>(
    private val dataFlow: Flow<UpdatableData<Key, Data>>,
) : DataSource<Key, Data, PagingStatus> {
    override suspend fun load(
        loadParams: LoadParams<Key>,
    ) = LoadResult.Success<Key, Data, PagingStatus>(dataFlow = dataFlow)
}

fun <Key : Any, Data : Any, PagingStatus : Any> DataSource<Key, Data, PagingStatus>.singlePage(
    itemsFlow: Flow<List<Data>>,
) = SinglePageDataSource<Key, Data, PagingStatus>(itemsFlow.map { items ->
    UpdatableData(items, null)
})

fun <Key : Any, Data : Any, PagingStatus : Any> DataSource<Key, Data, PagingStatus>.singlePage(
    items: List<Data>,
) = SinglePageDataSource<Key, Data, PagingStatus>(
    flow { emit(UpdatableData(items, null)) }
)
package ru.snowmaze.pagingflow.sources

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.result.LoadResult

class SinglePageDataSource<Key : Any, Data : Any>(
    private val dataFlow: Flow<UpdatableData<Key, Data>>,
) : DataSource<Key, Data> {

    companion object {

        fun <Key : Any, Data : Any> singlePageDataSource(
            data: Flow<List<Data>>
        ) = SinglePageDataSource<Key, Data>(data.map { items ->
            UpdatableData(items, null)
        })

        fun <Key : Any, Data : Any> singlePage(
            items: List<Data>,
        ) = SinglePageDataSource<Key, Data>(
            flow { emit(UpdatableData(items, null)) }
        )
    }

    override suspend fun load(
        loadParams: LoadParams<Key>,
    ) = LoadResult.Success<Key, Data>(dataFlow = dataFlow)
}
package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.source.PagingSource

class NothingToLoadSource<Key: Any, Data: Any>: PagingSource<Key, Data> {

    override suspend fun load(loadParams: LoadParams<Key>): LoadResult<Key, Data> {
        return LoadResult.NothingToLoad()
    }
}
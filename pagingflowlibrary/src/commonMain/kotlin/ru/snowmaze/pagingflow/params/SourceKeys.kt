package ru.snowmaze.pagingflow.params

import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.source.ConcatSourceData

object SourceKeys {

    fun <Key : Any> pageLoaderResult() =
        DataKey<ConcatSourceData<Key>>("page_loader_result")

    fun <Key : Any, Data : Any> sourceResultKey() =
        DataKey<LoadResult<Key, Data>>("source_result_key")
}
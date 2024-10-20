package ru.snowmaze.pagingflow.sources

import ru.snowmaze.pagingflow.params.PagingParams

data class ConcatSourceData<Key : Any>(
    val currentKey: Key?,
    val returnData: PagingParams?,
    val hasNext: Boolean
)
package ru.snowmaze.pagingflow.source

import ru.snowmaze.pagingflow.params.PagingParams

data class ConcatSourceData<Key : Any>(
    val currentKey: Key?,
    val returnData: PagingParams?,
    val hasNext: Boolean
)
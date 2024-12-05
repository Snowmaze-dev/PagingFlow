package ru.snowmaze.pagingflow.source

import ru.snowmaze.pagingflow.params.MutablePagingParams

data class ConcatSourceData<Key : Any>(
    val currentKey: Key?,
    val returnData: MutablePagingParams?,
    val hasNext: Boolean
)
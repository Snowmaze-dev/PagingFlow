package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.params.PagingParams

data class UpdatableData<Key, Data>(
    val data: List<Data>,
    val nextPageKey: Key? = null,
    val params: PagingParams? = null
)
package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.params.MutablePagingParams

class UpdatableData<Key, Data>(
    val data: List<Data?>,
    val nextPageKey: Key? = null,
    val params: MutablePagingParams? = null
)
package ru.snowmaze.pagingflow

class UpdatableData<Key, Data>(
    val data: List<Data>,
    val nextPageKey: Key? = null,
)
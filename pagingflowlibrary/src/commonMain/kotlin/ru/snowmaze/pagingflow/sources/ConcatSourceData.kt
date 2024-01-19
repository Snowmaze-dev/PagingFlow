package ru.snowmaze.pagingflow.sources

class ConcatSourceData<Key : Any>(val currentKey: Key?, val additionalData: Any? = null)
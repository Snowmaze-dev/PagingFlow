package ru.snowmaze.pagingflow.diff

import ru.snowmaze.pagingflow.PagingFlow

open class DataChangedEvent<Key : Any, Data : Any>

class PageAddedEvent<Key : Any, Data : Any>(
    val key: Key?,
    val pageIndex: Int,
    val sourceIndex: Int,
    val items: List<Data>
) : DataChangedEvent<Key, Data>()

class PageChangedEvent<Key : Any, Data : Any>(
    val key: Key?,
    val pageIndex: Int,
    val sourceIndex: Int,
    val items: List<Data?>
) : DataChangedEvent<Key, Data>()

class PageRemovedEvent<Key : Any, Data : Any>(
    val key: Key?,
    val pageIndex: Int,
    val sourceIndex: Int
) : DataChangedEvent<Key, Data>()

/**
 * @see [PagingFlow.invalidate]
 */
class InvalidateEvent<Key : Any, Data : Any> : DataChangedEvent<Key, Data>()
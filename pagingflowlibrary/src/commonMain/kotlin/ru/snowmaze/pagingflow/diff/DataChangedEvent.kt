package ru.snowmaze.pagingflow.diff

import ru.snowmaze.pagingflow.PagingFlow

open class DataChangedEvent<Key : Any, Data : Any>

open class PageChangedEvent<Key : Any, Data : Any>(
    val key: Key?,
    val pageIndex: Int,
    val sourceIndex: Int,
    val items: List<Data?>
) : DataChangedEvent<Key, Data>()

class PageAddedEvent<Key : Any, Data : Any>(
    key: Key?,
    pageIndex: Int,
    sourceIndex: Int,
    items: List<Data>
) : PageChangedEvent<Key, Data>(key, pageIndex, sourceIndex, items)

class PageRemovedEvent<Key : Any, Data : Any>(
    val key: Key?,
    val pageIndex: Int,
    val sourceIndex: Int
) : DataChangedEvent<Key, Data>()

/**
 * @see [PagingFlow.invalidate]
 */
class InvalidateEvent<Key : Any, Data : Any> : DataChangedEvent<Key, Data>()
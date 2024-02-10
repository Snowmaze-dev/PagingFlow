package ru.snowmaze.pagingflow.diff

import ru.snowmaze.pagingflow.PagingFlow

open class DataChangedEvent<Key : Any, Data : Any>

open class PageChangedEvent<Key : Any, Data : Any>(
    val key: Key?,
    val pageIndex: Int,
    val sourceIndex: Int,
    val previousList: List<Data?>? = null,
    val items: List<Data?>,
    val changeType: ChangeType = ChangeType.COMMON_CHANGE
) : DataChangedEvent<Key, Data>() {
    enum class ChangeType {
        CHANGE_TO_NULLS, CHANGE_FROM_NULLS_TO_ITEMS, COMMON_CHANGE
    }

    override fun toString(): String {
        return "PageChangedEvent(key=$key, pageIndex=$pageIndex, sourceIndex=$sourceIndex, previousList=$previousList, items=$items, changeType=$changeType)"
    }
}

class PageAddedEvent<Key : Any, Data : Any>(
    key: Key?,
    pageIndex: Int,
    sourceIndex: Int,
    items: List<Data?>
) : PageChangedEvent<Key, Data>(key, pageIndex, sourceIndex, null, items) {

    override fun toString(): String {
        return "PageAddedEvent(key=$key, pageIndex=$pageIndex, sourceIndex=$sourceIndex, previousList=$previousList, items=$items, changeType=$changeType)"
    }
}

class PageRemovedEvent<Key : Any, Data : Any>(
    val key: Key?,
    val pageIndex: Int,
    val sourceIndex: Int,
    val itemsCount: Int
) : DataChangedEvent<Key, Data>()

/**
 * @see [PagingFlow.invalidate]
 */
class InvalidateEvent<Key : Any, Data : Any> : DataChangedEvent<Key, Data>()
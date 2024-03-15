package ru.snowmaze.pagingflow.diff

import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.params.PagingParams

open class DataChangedEvent<Key : Any, Data : Any>

/**
 * When page changed this event is dispatched
 */
open class PageChangedEvent<Key : Any, Data : Any>(
    val key: Key?,
    val pageIndex: Int,
    val sourceIndex: Int,
    val previousList: List<Data?>? = null,
    val items: List<Data?>,
    val changeType: ChangeType = ChangeType.COMMON_CHANGE,
    val params: PagingParams? = null,
) : DataChangedEvent<Key, Data>() {

    fun requireParams() =
        params ?: throw IllegalStateException("Params of page event required to be not null.")

    enum class ChangeType {
        CHANGE_TO_NULLS, CHANGE_FROM_NULLS_TO_ITEMS, COMMON_CHANGE
    }
}

class PageAddedEvent<Key : Any, Data : Any>(
    key: Key?,
    pageIndex: Int,
    sourceIndex: Int,
    items: List<Data>,
    params: PagingParams? = null,
) : PageChangedEvent<Key, Data>(
    key = key,
    pageIndex = pageIndex,
    sourceIndex = sourceIndex,
    previousList = null,
    items = items, params = params
)

class PageRemovedEvent<Key : Any, Data : Any>(
    val key: Key?,
    val pageIndex: Int,
    val sourceIndex: Int,
    val itemsCount: Int,
) : DataChangedEvent<Key, Data>()

/**
 * @see [PagingFlow.invalidate]
 */
class InvalidateEvent<Key : Any, Data : Any> : DataChangedEvent<Key, Data>()

class AwaitDataSetEvent<Key : Any, Data : Any>(
    val callback: () -> Unit
) : DataChangedEvent<Key, Data>()
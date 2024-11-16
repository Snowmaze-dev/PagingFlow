package ru.snowmaze.pagingflow.diff

import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

open class DataChangedEvent<Key : Any, Data : Any>

internal interface EventFromDataSource<Key : Any, Data : Any> {

    val sourceIndex: Int

    val pageIndex: Int

    val pageIndexInSource: Int

    fun copyWithNewPositionData(
        sourceIndex: Int,
        pageIndex: Int,
        pageIndexInSource: Int = this.pageIndexInSource
    ): DataChangedEvent<Key, Data>
}

/**
 * When page changed this event is dispatched
 */
open class PageChangedEvent<Key : Any, Data : Any>(
    val key: Key?,
    override val sourceIndex: Int,
    override val pageIndex: Int,
    override val pageIndexInSource: Int,
    val previousList: List<Data?>? = null,
    val items: List<Data?>,
    val changeType: ChangeType = ChangeType.COMMON_CHANGE,
    val params: PagingParams? = null,
) : DataChangedEvent<Key, Data>(), EventFromDataSource<Key, Data> {

    inline val notNullItems get() = items as List<Data>

    fun requireParams() =
        params ?: throw IllegalStateException("Params of page event required to be not null.")

    enum class ChangeType {
        CHANGE_TO_NULLS, CHANGE_FROM_NULLS_TO_ITEMS, COMMON_CHANGE
    }

    override fun copyWithNewPositionData(
        sourceIndex: Int,
        pageIndex: Int,
        pageIndexInSource: Int
    ) = PageChangedEvent(
        key = key,
        sourceIndex = sourceIndex,
        pageIndex = pageIndex,
        pageIndexInSource = pageIndexInSource,
        previousList = previousList,
        items = items,
        changeType = changeType,
        params = params,
    )

    override fun toString(): String {
        return "${this::class.simpleName}(key=$key, sourceIndex=$sourceIndex, pageIndex=$pageIndex, pageIndexInSource=$pageIndexInSource, previousList=$previousList, items=$items, changeType=$changeType, params=$params)"
    }
}

class PageAddedEvent<Key : Any, Data : Any>(
    key: Key?,
    sourceIndex: Int,
    pageIndex: Int,
    pageIndexInSource: Int,
    items: List<Data>,
    params: PagingParams? = null,
) : PageChangedEvent<Key, Data>(
    key = key,
    pageIndex = pageIndex,
    sourceIndex = sourceIndex,
    pageIndexInSource = pageIndexInSource,
    previousList = null,
    items = items,
    params = params
) {

    override fun copyWithNewPositionData(
        sourceIndex: Int,
        pageIndex: Int,
        pageIndexInSource: Int
    ) = PageAddedEvent(
        key = key,
        sourceIndex = sourceIndex,
        pageIndex = pageIndex,
        items = items as List<Data>,
        params = params,
        pageIndexInSource = pageIndexInSource
    )
}

class PageRemovedEvent<Key : Any, Data : Any>(
    val key: Key?,
    override val sourceIndex: Int,
    override val pageIndex: Int,
    override val pageIndexInSource: Int,
    val itemsCount: Int,
) : DataChangedEvent<Key, Data>(), EventFromDataSource<Key, Data> {

    override fun copyWithNewPositionData(
        sourceIndex: Int,
        pageIndex: Int,
        pageIndexInSource: Int
    ) = PageRemovedEvent<Key, Data>(
        key = key,
        sourceIndex = sourceIndex,
        pageIndex = pageIndex,
        pageIndexInSource = pageIndexInSource,
        itemsCount = itemsCount
    )
}

/**
 * @see [PagingFlow.invalidate]
 */
class InvalidateEvent<Key : Any, Data : Any>(
    val invalidateBehavior: InvalidateBehavior? = null
) : DataChangedEvent<Key, Data>()

class AwaitDataSetEvent<Key : Any, Data : Any>(
    val callback: () -> Unit
) : DataChangedEvent<Key, Data>()

@OptIn(ExperimentalContracts::class)
inline fun <Key : Any, Data : Any, T : Any> DataChangedEvent<Key, Data>.handle(
    onPageAdded: (PageAddedEvent<Key, Data>) -> T?,
    onPageChanged: (PageChangedEvent<Key, Data>) -> T?,
    onPageRemovedEvent: (PageRemovedEvent<Key, Data>) -> T?,
    onInvalidate: (InvalidateEvent<Key, Data>) -> T?,
    onElse: ((DataChangedEvent<Key, Data>) -> T?) = { null }
): T? {
    contract {
        callsInPlace(onPageAdded, InvocationKind.EXACTLY_ONCE)
        callsInPlace(onPageChanged, InvocationKind.EXACTLY_ONCE)
        callsInPlace(onPageRemovedEvent, InvocationKind.EXACTLY_ONCE)
        callsInPlace(onInvalidate, InvocationKind.EXACTLY_ONCE)
        callsInPlace(onElse, InvocationKind.EXACTLY_ONCE)
    }

    return when (this) {
        is PageAddedEvent -> onPageAdded(this)
        is PageChangedEvent -> onPageChanged(this)
        is PageRemovedEvent -> onPageRemovedEvent(this)
        is InvalidateEvent -> onInvalidate(this)
        else -> onElse(this)
    }
}
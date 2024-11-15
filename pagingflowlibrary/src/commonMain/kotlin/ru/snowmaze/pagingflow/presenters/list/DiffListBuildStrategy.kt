package ru.snowmaze.pagingflow.presenters.list

import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent.ChangeType
import ru.snowmaze.pagingflow.diff.handle
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.utils.fastForEach
import ru.snowmaze.pagingflow.utils.fastSumOf

/**
 * Builds list using diff events to change items in copy or instance of already built list
 */
open class DiffListBuildStrategy<Key : Any, Data : Any> protected constructor(
    private val reuseList: Boolean
) : ListBuildStrategy<Key, Data> {

    constructor(): this(false)

    private val pageSizes = mutableMapOf<Int, Int>()
    override var list = if (reuseList) mutableListOf<Data?>() else emptyList()
    override var startPageIndex: Int = 0
    override var recentLoadData: List<PagingParams> = emptyList()

    override suspend fun buildList(
        events: List<DataChangedEvent<Key, Data>>,
        onInvalidate: (InvalidateBehavior?) -> Unit
    ) {
        val newRecentLoadData = ArrayList<PagingParams>(
            events.fastSumOf { if (it is PageChangedEvent && it.params != null) 1 else 0 }
        )
        recentLoadData = newRecentLoadData
        if (!reuseList) list = ArrayList(list)
        buildListInternal(list as MutableList<Data?>, events, onInvalidate)
    }

    private suspend inline fun buildListInternal(
        list: MutableList<Data?>,
        events: List<DataChangedEvent<Key, Data>>,
        crossinline onInvalidate: suspend (InvalidateBehavior?) -> Unit
    ) = events.fastForEach { event ->
        event.handle(
            onPageAdded = { current -> // TODO double added event inconsistent behaviour
                if (current.params != null) (recentLoadData as MutableList).add(current.params)
                removePageItemsAndAdd(
                    list = list,
                    pageIndex = current.pageIndex,
                    newItems = current.items
                )
            },
            onPageChanged = { current -> // TODO changed without added event inconsistent behaviour
                if (current.params != null) (recentLoadData as MutableList).add(current.params)
                if (current.changeType == ChangeType.CHANGE_TO_NULLS) {
                    startPageIndex += current.items.size
                } else if (current.changeType == ChangeType.CHANGE_FROM_NULLS_TO_ITEMS) {
                    startPageIndex -= pageSizes[current.pageIndex] ?: 0
                }
                removePageItemsAndAdd(
                    list = list,
                    pageIndex = current.pageIndex,
                    newItems = current.items
                )
            },
            onPageRemovedEvent = { current ->
                val startIndex = pageSizes.keys.calculateStartIndex(current.pageIndex)
                repeat(pageSizes[current.pageIndex] ?: 0) { list.removeAt(startIndex) }
                pageSizes.remove(current.pageIndex)
            },
            onInvalidate = { onInvalidate(it.invalidateBehavior) }
        )
    }

    private inline fun removePageItemsAndAdd(
        list: MutableList<Data?>,
        pageIndex: Int,
        newItems: List<Data?>
    ) {
        var startIndex = pageSizes.keys.calculateStartIndex(pageIndex)
        val itemCount = pageSizes[pageIndex] ?: 0
        val removeIndex = startIndex + newItems.size
        for (i in 0 until itemCount.coerceAtLeast(newItems.size)) {
            if (i >= newItems.size) list.removeAt(removeIndex)
            else if (list.size > startIndex && itemCount > i) list[startIndex] = newItems[i]
            else list.add(startIndex, newItems[i])
            startIndex++
        }
        pageSizes[pageIndex] = newItems.size
    }

    private inline fun Collection<Int>.calculateStartIndex(pageIndex: Int) = sumOf {
        if (it >= pageIndex) 0 else (pageSizes[it] ?: 0)
    }

    override fun invalidate() {
        (list as? MutableList)?.let { list ->
            list.clear()
            if (reuseList && list is ArrayList) list.trimToSize()
        }
        startPageIndex = 0
        pageSizes.clear()
    }
}
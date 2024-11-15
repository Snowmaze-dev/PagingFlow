package ru.snowmaze.pagingflow.presenters.list

import ru.snowmaze.pagingflow.DelicatePagingApi
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent.ChangeType
import ru.snowmaze.pagingflow.diff.handle
import ru.snowmaze.pagingflow.diff.mediums.BatchingPagingDataChangesMedium
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.utils.fastForEach
import ru.snowmaze.pagingflow.utils.fastSumOf

/**
 * Builds list using diff events to change items in already built list
 * Should be used with care when [reuseList] is true
 * in case when [reuseList] is true it's preferable to use event batching [BatchingPagingDataChangesMedium]
 * so using list from presenter wouldn't throw [ConcurrentModificationException] for short amount of time
 * while for example it being mapped to other list
 */
@DelicatePagingApi
class DiffListBuildStrategy<Key : Any, Data : Any>(
    private val reuseList: Boolean = false
) : ListBuildStrategy<Key, Data> {

    private val pageSizes = mutableMapOf<Int, Int>()
    override var list = mutableListOf<Data?>()
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
        buildListInternal(events, onInvalidate)
    }

    private suspend inline fun buildListInternal(
        events: List<DataChangedEvent<Key, Data>>,
        crossinline onInvalidate: suspend (InvalidateBehavior?) -> Unit
    ) = events.fastForEach { event ->
        if (event is PageChangedEvent && event.params != null) {
            (recentLoadData as MutableList).add(event.params)
        }
        event.handle(
            onPageAdded = { current -> // TODO double added event inconsistent behaviour
                removePageItemsAndAdd(current.pageIndex, current.items)
            },
            onPageChanged = { current -> // TODO changed without added event inconsistent behaviour
                if (current.changeType == ChangeType.CHANGE_TO_NULLS) {
                    startPageIndex += current.items.size
                } else if (current.changeType == ChangeType.CHANGE_FROM_NULLS_TO_ITEMS) {
                    startPageIndex -= pageSizes[current.pageIndex] ?: 0
                }
                removePageItemsAndAdd(current.pageIndex, current.items)
            },
            onPageRemovedEvent = { current ->
                val startIndex = pageSizes.keys.calculateStartIndex(current.pageIndex)
                repeat(pageSizes[current.pageIndex] ?: 0) { list.removeAt(startIndex) }
                pageSizes.remove(current.pageIndex)
            },
            onInvalidate = { onInvalidate(it.invalidateBehavior) }
        )
    }

    private inline fun removePageItemsAndAdd(pageIndex: Int, newItems: List<Data?>) {
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
        val list = list
        list.clear()
        if (list is ArrayList && reuseList) list.trimToSize()
        startPageIndex = 0
        pageSizes.clear()
    }
}
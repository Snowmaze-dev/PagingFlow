package ru.snowmaze.pagingflow.presenters.list

import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent.ChangeType
import ru.snowmaze.pagingflow.diff.handle
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.utils.fastForEach
import ru.snowmaze.pagingflow.utils.fastSumOf
import ru.snowmaze.pagingflow.utils.platformMapOf
import kotlin.math.max

/**
 * Builds list using diff events to change items in copy or instance of already built list
 */
open class DiffListBuildStrategy<Key : Any, Data : Any> protected constructor(
    private val reuseList: Boolean
) : ListBuildStrategy<Key, Data> {

    constructor() : this(false)

    private val pageSizes = platformMapOf<Int, Int>()
    override var list = if (reuseList) mutableListOf<Data?>() else emptyList()
    override var startPageIndex: Int = 0
    override var recentLoadData: List<PagingParams> = emptyList()
    private var minIndex: Int = 0
    private inline val mutableList get() = list as MutableList<Data?>
    private var isBuildingList = false

    override fun buildList(
        events: List<DataChangedEvent<Key, Data>>,
        onInvalidate: (InvalidateBehavior?) -> Unit
    ) {
        val newRecentLoadData = ArrayList<PagingParams>(
            events.fastSumOf { if (it is PageChangedEvent && it.params != null) 1 else 0 }
        )
        recentLoadData = newRecentLoadData
        if (!reuseList) list = ArrayList(list)
        isBuildingList = true
        buildListInternal(events, onInvalidate)
        isBuildingList = false
    }

    private inline fun buildListInternal(
        events: List<DataChangedEvent<Key, Data>>,
        onInvalidate: (InvalidateBehavior?) -> Unit
    ) = events.fastForEach { event ->
        event.handle(
            onPageAdded = { current -> // TODO double added event inconsistent behaviour
                if (current.params != null) (recentLoadData as MutableList).add(current.params)
                removePageItemsAndAdd(
                    list = mutableList,
                    pageIndex = current.pageIndex,
                    newItems = current.items
                )
                if (minIndex > current.pageIndex) minIndex--
            },
            onPageChanged = { current -> // TODO changed without added event inconsistent behaviour
                if (current.params != null) (recentLoadData as MutableList).add(current.params)
                if (current.changeType == ChangeType.CHANGE_TO_NULLS) {
                    startPageIndex += current.items.size
                } else if (current.changeType == ChangeType.CHANGE_FROM_NULLS_TO_ITEMS) {
                    startPageIndex -= pageSizes[current.pageIndex] ?: 0
                }
                removePageItemsAndAdd(
                    list = mutableList,
                    pageIndex = current.pageIndex,
                    newItems = current.items
                )
            },
            onPageRemovedEvent = { current ->
                val startIndex = calculateStartIndex(current.pageIndex)
                val mutableList = mutableList
                repeat(pageSizes[current.pageIndex] ?: 0) { mutableList.removeAt(startIndex) }
                pageSizes.remove(current.pageIndex)
                if (minIndex == current.pageIndex) minIndex++
            },
            onInvalidate = {
                onInvalidate(it.invalidateBehavior)
            }
        )
    }

    private inline fun removePageItemsAndAdd(
        list: MutableList<Data?>,
        pageIndex: Int,
        newItems: List<Data?>
    ) {
        var startIndex = calculateStartIndex(pageIndex)
        val itemCount = pageSizes[pageIndex] ?: 0
        val removeIndex = startIndex + newItems.size
        for (i in 0 until max(itemCount, newItems.size)) {
            if (i >= newItems.size) list.removeAt(removeIndex)
            else if (list.size > startIndex && itemCount > i) list[startIndex] = newItems[i]
            else list.add(startIndex, newItems[i])
            startIndex++
        }
        pageSizes[pageIndex] = newItems.size
    }

    private inline fun calculateStartIndex(pageIndex: Int): Int {
        var sum = 0
        for (i in minIndex until pageIndex) {
            sum += pageSizes[i] ?: 0
        }
        return sum
    }

    override fun invalidate() {
        if (reuseList) (list as? MutableList)?.let { list ->
            list.clear()
            if (list is ArrayList) list.trimToSize()
        } else if (isBuildingList) list = arrayListOf()
        else list = emptyList()
        startPageIndex = 0
        minIndex = 0
        pageSizes.clear()
    }
}
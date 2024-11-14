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
            events.fastSumOf { if (it is PageChangedEvent) if (it.params == null) 0 else 1 else 0 }
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
                val startIndex = removePageItems(current.pageIndex)
                list.addAll(startIndex, current.items)
                pageSizes[current.pageIndex] = current.items.size
            },
            onPageChanged = { current -> // TODO changed without added event inconsistent behaviour
                val startIndex = removePageItems(current.pageIndex)
                if (current.changeType == ChangeType.CHANGE_TO_NULLS) {
                    startPageIndex += current.items.size
                } else if (current.changeType == ChangeType.CHANGE_FROM_NULLS_TO_ITEMS) {
                    startPageIndex -= pageSizes[current.pageIndex] ?: 0
                }
                list.addAll(startIndex, current.items)
                pageSizes[current.pageIndex] = current.items.size
            },
            onPageRemovedEvent = { current ->
                removePageItems(current.pageIndex)
                pageSizes.remove(current.pageIndex)
            },
            onInvalidate = {
                onInvalidate(it.invalidateBehavior)
            }
        )
    }

    private inline fun removePageItems(pageIndex: Int): Int {
        val startIndex = pageSizes.keys.sorted().calculateStartIndex(pageIndex)
        repeat(pageSizes[pageIndex] ?: 0) { list.removeAt(startIndex) }
        return startIndex
    }

    private inline fun List<Int>.calculateStartIndex(pageIndex: Int) = fastSumOf {
        if (it >= pageIndex) null else pageSizes[it]
    }

    override fun invalidate() {
        val list = list
        list.clear()
        if (list is ArrayList && reuseList) list.trimToSize()
        startPageIndex = 0
        pageSizes.clear()
    }
}
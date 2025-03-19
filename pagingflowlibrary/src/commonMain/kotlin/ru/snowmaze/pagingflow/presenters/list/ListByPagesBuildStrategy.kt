package ru.snowmaze.pagingflow.presenters.list

import androidx.collection.MutableScatterMap
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.handle
import ru.snowmaze.pagingflow.params.MutablePagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.utils.fastForEach
import ru.snowmaze.pagingflow.utils.fastSumOf

class ListByPagesBuildStrategy<Key : Any, Data : Any> : ListBuildStrategy<Key, Data> {

    private val indexedPages = MutableScatterMap<Int, PageChangedEvent<Key, Data>>()
    override var startPageIndex: Int = 0
    override var recentLoadData: List<MutablePagingParams> = emptyList()
    private var minIndex = 0

    /**
     * Flattens [indexedPages] Map to list
     */
    override fun buildList(
        events: List<DataChangedEvent<Key, Data>>,
        onInvalidate: (InvalidateBehavior?) -> Unit
    ): List<Data?> {
        val newRecentLoadData = ArrayList<MutablePagingParams>(
            events.fastSumOf { if (it is PageChangedEvent && it.params != null) 1 else 0 }
        )
        events.fastForEach { event ->
            event.handle(
                onPageAdded = {
                    if (it.params != null) newRecentLoadData.add(it.params)
                    indexedPages[it.pageIndex] = it
                    if (minIndex > it.pageIndex) minIndex--
                },
                onPageChanged = {
                    if (it.params != null) newRecentLoadData.add(it.params)
                    indexedPages[it.pageIndex] = it
                },
                onPageRemovedEvent = {
                    indexedPages.remove(it.pageIndex)
                    if (minIndex == it.pageIndex) minIndex++
                },
                onInvalidate = { onInvalidate(it.invalidateBehavior) },
                onElse = {}
            )
        }
        var newStartIndex = 0
        val indexes = minIndex until minIndex + indexedPages.size
        var listSize = 0
        for (pageIndex in indexes) {
            listSize += indexedPages[pageIndex]?.items?.size ?: 0
        }
        startPageIndex = newStartIndex
        recentLoadData = newRecentLoadData
        return buildList(listSize) {
            for (pageIndex in indexes) {
                val page = indexedPages[pageIndex] ?: continue
                if (page.changeType == PageChangedEvent.ChangeType.CHANGE_TO_NULLS) {
                    newStartIndex += page.items.size
                }
                addAll(page.items)
            }
        }
    }

    override fun invalidate() {
        indexedPages.clear()
    }
}
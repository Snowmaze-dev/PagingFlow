package ru.snowmaze.pagingflow.presenters.list

import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.handle
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.utils.fastForEach
import ru.snowmaze.pagingflow.utils.fastSumOf

class ListByPagesBuildStrategy<Key : Any, Data : Any> : ListBuildStrategy<Key, Data> {

    private val indexedPages = mutableMapOf<Int, PageChangedEvent<Key, Data>>()
    override var list = emptyList<Data?>()
    override var startPageIndex: Int = 0
    override var recentLoadData: List<PagingParams> = emptyList()

    /**
     * Flattens [indexedPages] Map to list
     */
    override suspend fun buildList(
        events: List<DataChangedEvent<Key, Data>>,
        onInvalidate: (InvalidateBehavior?) -> Unit
    ) {
        val newRecentLoadData = ArrayList<PagingParams>(
            events.fastSumOf { if (it is PageChangedEvent && it.params != null) 1 else 0 }
        )
        events.fastForEach { event ->
            event.handle(
                onPageAdded = {
                    if (it.params != null) newRecentLoadData.add(it.params)
                    indexedPages[it.pageIndex] = it
                },
                onPageChanged = {
                    if (it.params != null) newRecentLoadData.add(it.params)
                    indexedPages[it.pageIndex] = it
                },
                onPageRemovedEvent = { indexedPages.remove(it.pageIndex) },
                onInvalidate = { onInvalidate(it.invalidateBehavior) },
            )
        }
        val pageIndexesKeys = indexedPages.keys.sorted()
        var newStartIndex = 0
        list = buildList(pageIndexesKeys.fastSumOf { indexedPages[it]!!.items.size }) {
            pageIndexesKeys.fastForEach { pageIndex ->
                val page = indexedPages[pageIndex]!!
                if (page.changeType == PageChangedEvent.ChangeType.CHANGE_TO_NULLS) {
                    newStartIndex += page.items.size
                }
                addAll(page.items)
            }
        }
        startPageIndex = newStartIndex
        recentLoadData = newRecentLoadData
    }

    override fun invalidate() {
        indexedPages.clear()
    }
}
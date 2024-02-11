package ru.snowmaze.pagingflow.recycler

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.handle
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.SimpleBuildListPagingPresenter

class DispatchUpdatesToCallbackPresenter<Data : Any>(
    private val listUpdateCallback: ListUpdateCallback,
    private val offsetListUpdateCallbackProvider: (Int) -> OffsetListUpdateCallback,
    private val pagingMedium: PagingDataChangesMedium<out Any, Data>,
    private val itemCallback: DiffUtil.ItemCallback<Data>,
    invalidateBehavior: InvalidateBehavior,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : SimpleBuildListPagingPresenter<Any, Data>(
    pagingDataChangesMedium = pagingMedium as PagingDataChangesMedium<Any, Data>,
    invalidateBehavior = invalidateBehavior
) {

    private val pagesIndexes = mutableMapOf<Int, List<Data?>>()
    private var beforeInvalidateListSize = 0
    private var wasInvalidated = false

    override fun onItemsSet(
        events: List<DataChangedEvent<Any, Data>>,
        previousList: List<Data?>
    ) {
        coroutineScope.launch(mainDispatcher) {
            for (event in events) {
                event.handle(
                    onPageAdded = {
                        if (wasInvalidated) {
                            listUpdateCallback.onRemoved(0, beforeInvalidateListSize)
                            wasInvalidated = false
                        }
                        pagesIndexes[it.pageIndex] = it.items
                        listUpdateCallback.onInserted(
                            calculatePageStartItemIndex(it.pageIndex),
                            it.items.size
                        )
                    },
                    onPageChanged = { onPageChanged(it) },
                    onPageRemovedEvent = {
                        val list = pagesIndexes.remove(it.pageIndex)
                        listUpdateCallback.onRemoved(
                            calculatePageStartItemIndex(it.pageIndex),
                            list?.size ?: 0
                        )
                    },
                    onInvalidate = {}
                )
            }
        }
    }

    private fun onPageChanged(event: PageChangedEvent<*, Data>) {
        val offsetListUpdateCallback = offsetListUpdateCallbackProvider(
            calculatePageStartItemIndex(event.pageIndex)
        )
        when (event.changeType) {
            PageChangedEvent.ChangeType.COMMON_CHANGE -> coroutineScope.launch(
                pagingMedium.config.processingDispatcher
            ) {
                val diffResult = PagingDiffUtil.calculateDiff(
                    diffCallback = itemCallback,
                    oldList = pagesIndexes.getValue(event.pageIndex) as List<Data>,
                    newList = event.items as List<Data>
                )
                withContext(mainDispatcher) {
                    diffResult.dispatchUpdatesTo(offsetListUpdateCallback)
                }
            }

            else -> offsetListUpdateCallback.onChanged(0, event.items.size, null)
        }
        pagesIndexes[event.pageIndex] = event.items
    }

    override fun afterInvalidatedAction(previousList: List<Data?>) {
        super.afterInvalidatedAction(previousList)
        coroutineScope.launch(mainDispatcher) {
            pagesIndexes.clear()
            if (invalidateBehavior == InvalidateBehavior.INVALIDATE_IMMEDIATELY) {
                listUpdateCallback.onRemoved(0, previousList.size)
            } else {
                wasInvalidated = true
                beforeInvalidateListSize = previousList.size
            }
        }
    }

    private fun calculatePageStartItemIndex(pageIndex: Int): Int {
        var iterateIndex = 0
        var currentItemIndex = 0
        if (pageIndexes.isEmpty()) return 0
        while (true) {
            if (pageIndex == iterateIndex) return currentItemIndex
            currentItemIndex += pagesIndexes[iterateIndex]?.size ?: 0
            iterateIndex++
        }
    }
}
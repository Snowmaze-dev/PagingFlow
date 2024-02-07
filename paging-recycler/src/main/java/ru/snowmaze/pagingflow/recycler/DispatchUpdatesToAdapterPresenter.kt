package ru.snowmaze.pagingflow.recycler

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.handle
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.SimpleBuildListPagingPresenter

// unit test
class DispatchUpdatesToAdapterPresenter<Data : Any>(
    private val listUpdateCallback: ListUpdateCallback,
    private val offsetListUpdateCallbackProvider: (Int) -> OffsetListUpdateCallback,
    pagingMedium: PagingDataChangesMedium<out Any, Data>,
    private val itemCallback: DiffUtil.ItemCallback<Data>,
    invalidateBehavior: InvalidateBehavior,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : SimpleBuildListPagingPresenter<Any, Data>(
    pagingMedium as PagingDataChangesMedium<Any, Data>,
    invalidateBehavior
) {

    private val pagesIndexes = mutableMapOf<Int, List<Data?>>()

    override fun onItemsSet(
        events: List<DataChangedEvent<Any, Data>>,
        previousList: List<Data?>
    ) {
        coroutineScope.launch(mainDispatcher) {
            for (event in events) {
                event.handle(
                    onPageAdded = {
                        pagesIndexes[it.pageIndex] = it.items
                        listUpdateCallback.onInserted(
                            calculatePageStartItemIndex(it.pageIndex),
                            it.items.size
                        )
                    },
                    onPageChanged = {
                        val offsetListUpdateCallback =
                            offsetListUpdateCallbackProvider(calculatePageStartItemIndex(it.pageIndex))
                        when (it.changeType) {
                            PageChangedEvent.ChangeType.COMMON_CHANGE -> PagingDiffUtil.dispatchDiff(
                                itemCallback as DiffUtil.ItemCallback<Data>,
                                pagesIndexes.getValue(it.pageIndex) as List<Data>,
                                it.items as List<Data>,
                                offsetListUpdateCallback
                            )

                            else -> offsetListUpdateCallback.onChanged(0, it.items.size, null)
                        }
                        pagesIndexes[it.pageIndex] = it.items
                    },
                    onPageRemovedEvent = {
                        val list = pagesIndexes.remove(it.pageIndex)
                        listUpdateCallback.onRemoved(
                            calculatePageStartItemIndex(it.pageIndex),
                            list?.size ?: 0
                        )
                    },

                    // TODO implement invalidate behavior
                    onInvalidate = {
                        pagesIndexes.clear()
                        listUpdateCallback.onRemoved(0, previousList.size)
                    }
                )
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
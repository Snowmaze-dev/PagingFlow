package ru.snowmaze.pagingflow.recycler

import android.util.Log
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.handle
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.SimpleBuildListPagingPresenter

class DispatchUpdatesToAdapterPresenter<Data : Any>(
    private val adapter: RecyclerView.Adapter<*>,
    pagingMedium: PagingDataChangesMedium<out Any, Data>,
    private val itemCallback: DiffUtil.ItemCallback<Data>,
    invalidateBehavior: InvalidateBehavior,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : SimpleBuildListPagingPresenter<Any, Data>(
    pagingMedium as PagingDataChangesMedium<Any, Data>,
    invalidateBehavior
) {

    private val pagesIndexes = mutableMapOf<Int, List<Data?>>()

    override fun onItemsSet(events: List<DataChangedEvent<Any, Data>>) {
        coroutineScope.launch(mainDispatcher) {
            for (event in events) {
                event.handle(
                    onPageAdded = {
                        pagesIndexes[it.pageIndex] = it.items
                        adapter.notifyItemRangeInserted(
                            calculatePageStartItemIndex(it.pageIndex),
                            it.items.size
                        )
                    },
                    onPageChanged = {
                        val offsetListUpdateCallback = OffsetListUpdateCallback(
                            adapter,
                            calculatePageStartItemIndex(it.pageIndex)
                        )
                        when (it.changeType) {
                            PageChangedEvent.ChangeType.COMMON_CHANGE -> PagingDiffUtil.dispatchDiff(
                                itemCallback as DiffUtil.ItemCallback<Data>,
                                pagesIndexes.getValue(it.pageIndex) as List<Data>,
                                it.items as List<Data>,
                                offsetListUpdateCallback
                            )

                            else -> adapter.notifyItemRangeChanged(0, it.items.size)
                        }
                        pagesIndexes[it.pageIndex] = it.items
                    },
                    onPageRemovedEvent = {
                        val list = pagesIndexes.remove(it.pageIndex)
                        adapter.notifyItemRangeRemoved(
                            calculatePageStartItemIndex(it.pageIndex),
                            list?.size ?: 0
                        )
                    },
                    onInvalidate = {
                        pagesIndexes.clear()
                        adapter.notifyItemRangeRemoved(0, adapter.itemCount)
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
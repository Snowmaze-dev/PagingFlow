package ru.snowmaze.pagingflow.recycler

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.handle
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.LatestData
import ru.snowmaze.pagingflow.presenters.BasicPresenterConfiguration
import ru.snowmaze.pagingflow.presenters.BasicBuildListPagingPresenter
import ru.snowmaze.pagingflow.utils.fastForEach

class DispatchUpdatesToCallbackPresenter<Data : Any>(
    private val listUpdateCallback: ListUpdateCallback,
    private val offsetListUpdateCallbackProvider: (Int) -> OffsetListUpdateCallback,
    private val pagingMedium: PagingEventsMedium<out Any, Data>,
    private val itemCallback: DiffUtil.ItemCallback<Data>,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val presenterConfiguration: BasicPresenterConfiguration<out Any, Data>,
    private val setNewItems: (List<Data?>) -> Unit,
) : BasicBuildListPagingPresenter<Any, Data>(
    pagingEventsMedium = pagingMedium as PagingEventsMedium<Any, Data>,
    presenterConfiguration = presenterConfiguration as BasicPresenterConfiguration<Any, Data>
) {

    private val pagesIndexes = mutableMapOf<Int, List<Data?>>()
    private var beforeInvalidateListSize = 0
    private var wasInvalidated = false
    private var currentData = LatestData<Data>(emptyList(), emptyList())

    override suspend fun onEvent(event: PagingEvent<Any, Data>) {
        withContext(processingContext) {
            mutex.withLock {
                buildList(listOf(event))
            }
        }
    }

    override suspend fun onItemsSet(
        events: List<PagingEvent<Any, Data>>,
        currentData: LatestData<Data>
    ) {
        coroutineScope.launch(mainDispatcher) {
            this@DispatchUpdatesToCallbackPresenter.currentData = currentData
            setNewItems(currentData.data)
            // TODO make that cycle in background thread creating diff changes and then dispatch these updates in main thread
            events.fastForEach { event ->
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
                    onInvalidate = {
                    },
                    onElse = {}
                )
            }
        }.join()
    }

    private inline fun onPageChanged(event: PageChangedEvent<*, Data>) {
        val startIndex = calculatePageStartItemIndex(event.pageIndex)
        val offsetListUpdateCallback = offsetListUpdateCallbackProvider(startIndex)

        when (event.changeType) {
            PageChangedEvent.ChangeType.COMMON_CHANGE -> {
                val diffResult = PagingDiffUtil.calculateDiff(
                    oldList = pagesIndexes[event.pageIndex] as? List<Data> ?: emptyList(),
                    newList = event.items as List<Data>,
                    diffCallback = itemCallback,
                )
                diffResult.dispatchUpdatesTo(offsetListUpdateCallback)
            }

            else -> {
                offsetListUpdateCallback.onChanged(0, event.items.size, null)
                if (event.previousItemCount > event.items.size) {
                    offsetListUpdateCallback.onRemoved(
                        event.items.size,
                        event.previousItemCount - event.items.size
                    )
                }
            }
        }
        pagesIndexes[event.pageIndex] = event.items
    }


    override fun afterInvalidatedAction(
        invalidateBehavior: InvalidateBehavior
    ) {
        coroutineScope.launch(mainDispatcher) {
            pagesIndexes.clear()
            val previousList = currentData.data
            if (invalidateBehavior == InvalidateBehavior.INVALIDATE_IMMEDIATELY) {
                listUpdateCallback.onRemoved(0, previousList.size)
            } else {
                wasInvalidated = true
                beforeInvalidateListSize = previousList.size
            }
            currentData = LatestData(emptyList(), emptyList())
        }
    }

    private fun calculatePageStartItemIndex(pageIndex: Int): Int {
        var iterateIndex = 0
        var currentItemIndex = 0
        if (pagesIndexes.isEmpty()) return 0
        while (true) {
            if (pageIndex == iterateIndex) return currentItemIndex
            currentItemIndex += pagesIndexes[iterateIndex]?.size ?: 0
            iterateIndex++
        }
    }
}
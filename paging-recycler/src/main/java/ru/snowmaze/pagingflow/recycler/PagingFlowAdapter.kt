package ru.snowmaze.pagingflow.recycler

import androidx.annotation.CallSuper
import androidx.recyclerview.widget.AdapterListUpdateCallback
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.utils.PagingTrigger

/**
 * RecyclerView.Adapter base class which can listen paging data changes callback, calculate diffs on background thread and notify adapter of changes
 * This class wraps [DispatchUpdatesToCallbackPresenter] which listens events and dispatches updates to adapter through [ListUpdateCallback]
 * @see PagingTrigger
 * @see PagingDataChangesMedium
 * @see InvalidateBehavior
 */
abstract class PagingFlowAdapter<Data : Any, VH : ViewHolder>(
    itemCallback: DiffUtil.ItemCallback<Data>,
    pagingDataChangesMedium: PagingDataChangesMedium<out Any, Data>,
    private val pagingTrigger: PagingTrigger,
    invalidateBehavior: InvalidateBehavior = InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : RecyclerView.Adapter<VH>() {

    @Suppress("LeakingThis")
    private val dispatchUpdatesToCallbackPresenter = DispatchUpdatesToCallbackPresenter(
        listUpdateCallback = AdapterListUpdateCallback(this),
        offsetListUpdateCallbackProvider = { offset: Int ->
            OffsetListUpdateCallback(this, offset)
        },
        pagingMedium = pagingDataChangesMedium,
        itemCallback = itemCallback,
        invalidateBehavior = invalidateBehavior
    )
    private var items = emptyList<Data?>()

    init {
        pagingDataChangesMedium.config.coroutineScope.launch(mainDispatcher) {
            dispatchUpdatesToCallbackPresenter.dataFlow.collect {
                items = it
            }
        }
        pagingTrigger.currentStartIndexProvider = { dispatchUpdatesToCallbackPresenter.startIndex }
        pagingTrigger.itemCount = { itemCount }
        pagingTrigger.currentTimeMillisProvider = { System.currentTimeMillis() }
    }

    @CallSuper
    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
        pagingTrigger.onItemVisible(position)
    }

    override fun getItemCount() = items.size

    fun getItem(index: Int) = items[index]
}
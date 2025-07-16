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
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.utils.PagingTrigger

/**
 * RecyclerView.Adapter base class which can listen paging data changes callback, calculate diffs on background thread and notify adapter of changes
 * This class wraps [DispatchUpdatesToCallbackPresenter] which listens events and dispatches updates to adapter through [ListUpdateCallback]
 * @see PagingTrigger
 * @see PagingEventsMedium
 * @see InvalidateBehavior
 */
abstract class PagingFlowAdapter<Data : Any, VH : ViewHolder>(
    itemCallback: DiffUtil.ItemCallback<Data>,
    pagingEventsMedium: PagingEventsMedium<out Any, Data>,
    private val pagingTrigger: PagingTrigger,
    invalidateBehavior: InvalidateBehavior = InvalidateBehavior.WAIT_FOR_NEW_LIST,
) : RecyclerView.Adapter<VH>() {

    @Suppress("LeakingThis")
    protected val dispatchUpdatesToCallbackPresenter = DispatchUpdatesToCallbackPresenter(
        listUpdateCallback = AdapterListUpdateCallback(this),
        offsetListUpdateCallbackProvider = { offset: Int ->
            OffsetListUpdateCallback(this, offset)
        },
        pagingMedium = pagingEventsMedium,
        itemCallback = itemCallback,
        invalidateBehavior = invalidateBehavior
    ) {
        items = it
    }
    private var items = emptyList<Data?>()
    val startIndex get() = dispatchUpdatesToCallbackPresenter.startIndex

    init {
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

    fun getItemNullable(index: Int) = items[index]

    operator fun get(index: Int) = items[index] as Data
}
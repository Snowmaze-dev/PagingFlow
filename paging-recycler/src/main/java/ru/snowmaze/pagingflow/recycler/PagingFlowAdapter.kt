package ru.snowmaze.pagingflow.recycler

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior

@Suppress("LeakingThis")
abstract class PagingFlowAdapter<Data : Any, VH : ViewHolder>(
    itemCallback: DiffUtil.ItemCallback<Data>,
    pagingDataChangesMedium: PagingDataChangesMedium<out Any, Data>,
    invalidateBehavior: InvalidateBehavior = InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : RecyclerView.Adapter<VH>() {

    private val dispatchUpdatesToAdapterPresenter = DispatchUpdatesToAdapterPresenter(
        this,
        pagingDataChangesMedium,
        itemCallback,
        invalidateBehavior
    )
    private var items = listOf<Data?>()

    init {
        pagingDataChangesMedium.config.coroutineScope.launch(mainDispatcher) {
            dispatchUpdatesToAdapterPresenter.dataFlow.collect {
                items = it
            }
        }
    }

    override fun getItemCount() = items.size

    fun getItem(index: Int) = items[index]
}
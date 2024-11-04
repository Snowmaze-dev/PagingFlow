package ru.snowmaze.pagingflow.recycler

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback

object PagingDiffUtil {

    fun <T : Any> calculateDiff(
        oldList: List<T>,
        newList: List<T>,
        diffCallback: DiffUtil.ItemCallback<T>,
    ) = DiffUtil.calculateDiff(
        object : DiffUtil.Callback() {
            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                val oldItem = oldList[oldItemPosition]
                val newItem = oldList[newItemPosition]

                return when {
                    oldItem === newItem -> true
                    else -> diffCallback.getChangePayload(oldItem, newItem)
                }
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList.getOrNull(oldItemPosition)
                val newItem = newList.getOrNull(newItemPosition)
                return when {
                    oldItem === newItem -> true
                    newItem == null || oldItem == null -> return false
                    else -> diffCallback.areItemsTheSame(oldItem, newItem)
                }
            }

            override fun areContentsTheSame(
                oldItemPosition: Int,
                newItemPosition: Int
            ): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]

                return when {
                    oldItem === newItem -> true
                    else -> diffCallback.areContentsTheSame(oldItem, newItem)
                }
            }


            override fun getOldListSize() = oldList.size

            override fun getNewListSize() = newList.size
        },
        true
    )
}
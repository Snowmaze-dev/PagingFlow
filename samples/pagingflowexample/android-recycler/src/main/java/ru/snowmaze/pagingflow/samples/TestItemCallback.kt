package ru.snowmaze.pagingflow.samples

import androidx.recyclerview.widget.DiffUtil

class TestItemCallback : DiffUtil.ItemCallback<TestItem>() {

    override fun areItemsTheSame(oldItem: TestItem, newItem: TestItem): Boolean {
        return if (oldItem == null && newItem == null) true else oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: TestItem, newItem: TestItem): Boolean {
        return if (oldItem == null && newItem == null) true else oldItem == newItem
    }
}
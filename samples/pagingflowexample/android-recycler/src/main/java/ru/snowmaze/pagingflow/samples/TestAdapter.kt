package ru.snowmaze.pagingflow.samples

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import ru.snowmaze.pagingflow.PagingTrigger

class TestAdapter(
    private val pagingTrigger: PagingTrigger
) : ListAdapter<String, TestAdapter.TestViewHolder>(StringItemCallback()) {

    class TestViewHolder(itemView: View) : ViewHolder(itemView)

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ) = TestViewHolder(AppCompatTextView(parent.context).apply {
        val dp = resources.displayMetrics.density
        updatePadding(left = (dp * 8).toInt(), right = (dp * 8).toInt(), top = (dp * 4).toInt())
    })

    override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
        (holder.itemView as TextView).text = getItem(position)
        pagingTrigger.onItemVisible(position)
    }
}
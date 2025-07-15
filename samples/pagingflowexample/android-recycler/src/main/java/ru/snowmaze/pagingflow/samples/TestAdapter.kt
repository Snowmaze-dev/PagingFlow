package ru.snowmaze.pagingflow.samples

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.recycler.PagingFlowAdapter
import ru.snowmaze.pagingflow.utils.PagingTrigger

class TestAdapter(
    pagingTrigger: PagingTrigger,
    pagingEventsMedium: PagingEventsMedium<Int, String>,
) : PagingFlowAdapter<String, TestAdapter.TestViewHolder>(
    itemCallback = StringItemCallback(),
    pagingEventsMedium = pagingEventsMedium,
    pagingTrigger = pagingTrigger
) {

    class TestViewHolder(itemView: View) : ViewHolder(itemView)

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ) = TestViewHolder(AppCompatTextView(parent.context).apply {
        val dp = resources.displayMetrics.density
        updatePadding(left = (dp * 8).toInt(), right = (dp * 8).toInt(), top = (dp * 4).toInt())
    })

    override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
        (holder.itemView as TextView).text = getItem(position)
    }
}
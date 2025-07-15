package ru.snowmaze.pagingflow.samples

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.android.material.progressindicator.CircularProgressIndicator
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.recycler.PagingFlowAdapter
import ru.snowmaze.pagingflow.utils.PagingTrigger

class TestAdapter(
    pagingTrigger: PagingTrigger,
    pagingEventsMedium: PagingEventsMedium<Int, TestItem>,
) : PagingFlowAdapter<TestItem, BaseViewHolder<TestItem>>(
    itemCallback = TestItemCallback(),
    pagingEventsMedium = pagingEventsMedium,
    pagingTrigger = pagingTrigger
) {

    companion object {
        const val ITEM = 0
        const val LOADER = 1
    }

    class TestViewHolder(val textView: TextView) : BaseViewHolder<TestItem.Item?>(textView) {

        override fun bind(item: TestItem.Item?) {
            textView.background = if (item == null) GradientDrawable().apply {
                cornerRadius = 8f
                setColor(Color.GRAY)
            } else null
            textView.text = item?.text
        }
    }

    class LoaderViewHolder(itemView: View) : BaseViewHolder<TestItem.Loader>(itemView) {

        override fun bind(item: TestItem.Loader) {}
    }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ) = (when (viewType) {
        ITEM -> TestViewHolder(AppCompatTextView(parent.context).apply {
            val dp = resources.displayMetrics.density
            layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                leftMargin = (dp * 8).toInt()
                rightMargin = (dp * 8).toInt()
                topMargin = (dp * 8).toInt()
            }
        })

        LOADER -> LoaderViewHolder(
            FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                updatePadding(top = 16, bottom = 16)
                addView(CircularProgressIndicator(parent.context).apply {

                    isIndeterminate = true
                    layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                })
            }
        )

        else -> throw IllegalArgumentException("Not supported type.")
    }) as BaseViewHolder<TestItem>

    override fun onBindViewHolder(holder: BaseViewHolder<TestItem>, position: Int) {
        val item = getItemNullable(position)
        if (item == null) {
            (holder as BaseViewHolder<TestItem?>).bind(null)
        } else holder.bind(item)
    }

    override fun getItemViewType(position: Int) = when (getItemNullable(position)) {
        null -> ITEM
        is TestItem.Item -> ITEM
        is TestItem.Loader -> LOADER
    }
}

abstract class BaseViewHolder<T>(itemView: View) : ViewHolder(itemView) {

    abstract fun bind(item: T)
}
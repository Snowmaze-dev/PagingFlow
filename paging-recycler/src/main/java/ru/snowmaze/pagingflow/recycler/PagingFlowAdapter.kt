package ru.snowmaze.pagingflow.recycler

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder

class PagingFlowAdapter<VH: ViewHolder>: RecyclerView.Adapter<VH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = TODO()

    override fun getItemCount() = 0

    override fun onBindViewHolder(holder: VH, position: Int) {
    }
}
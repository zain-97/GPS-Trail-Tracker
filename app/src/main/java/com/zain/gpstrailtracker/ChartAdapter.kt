package com.zain.gpstrailtracker

import androidx.recyclerview.widget.RecyclerView
import com.zain.gpstrailtracker.ChartAdapter.BarViewHolder
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import kotlinx.android.synthetic.main.bar_chart_item.view.*

class ChartAdapter(private val speed_list: List<Float>) :
    RecyclerView.Adapter<BarViewHolder>() {

    inner class BarViewHolder(v: View) : RecyclerView.ViewHolder(v) {

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.bar_chart_item, parent, false)
        return BarViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: BarViewHolder, position: Int) {
        val params = holder.itemView.barView.layoutParams
        params.height = (speed_list[position] * 25f).toInt()
        holder.itemView.barView.requestLayout()
        val labelString=(position+1).toString()
        holder.itemView.label.text=labelString
    }

    override fun getItemCount(): Int {
        return speed_list.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
}
package com.myra.assistant.ui.usage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
class UsageAdapter : RecyclerView.Adapter<UsageAdapter.ViewHolder>() {

    private val items = mutableListOf<AppUsageInfo>()

    fun submitList(newItems: List<AppUsageInfo>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val appIcon = view.findViewById<ImageView>(R.id.appIcon)
        private val appName = view.findViewById<TextView>(R.id.appName)
        private val appUsageTime = view.findViewById<TextView>(R.id.appUsageTime)

        fun bind(item: AppUsageInfo) {
            appName.text = item.appName
            item.icon?.let { appIcon.setImageDrawable(it) }

            appUsageTime.text = UsageTimeFormatter.formatDuration(item.timeInForegroundMs)
        }
    }
}

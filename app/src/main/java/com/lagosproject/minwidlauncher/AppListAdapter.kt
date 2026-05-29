package com.lagosproject.minwidlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.Normalizer

private val DIACRITICS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun normalizeText(text: String): String {
    val tmp = Normalizer.normalize(text, Normalizer.Form.NFD)
    return tmp.replace(DIACRITICS_REGEX, "").lowercase()
}

data class AppInfo(
    val label: String,
    val packageName: String,
    val normalizedLabel: String = normalizeText(label)
)

class AppListAdapter(
    private var apps: List<AppInfo>,
    private val textSize: Float,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppVH>() {

    private var filtered: List<AppInfo> = apps

    inner class AppVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppVH(view)
    }

    override fun onBindViewHolder(holder: AppVH, position: Int) {
        val app = filtered[position]
        holder.tvName.text = app.label
        holder.tvName.textSize = textSize
        holder.itemView.setOnClickListener { onAppClick(app) }
        holder.itemView.setOnLongClickListener {
            onAppLongClick(app)
            true
        }
    }

    override fun getItemCount(): Int = filtered.size

    fun filter(query: String) {
        val normalizedQuery = normalizeText(query)
        val newFiltered = if (normalizedQuery.isBlank()) {
            apps
        } else {
            apps.filter { it.normalizedLabel.contains(normalizedQuery) }
        }

        val diffCallback = object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = filtered.size
            override fun getNewListSize(): Int = newFiltered.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                filtered[oldItemPosition].packageName == newFiltered[newItemPosition].packageName
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                filtered[oldItemPosition] == newFiltered[newItemPosition]
        }
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        filtered = newFiltered
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateList(newApps: List<AppInfo>) {
        val diffCallback = object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = filtered.size
            override fun getNewListSize(): Int = newApps.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                filtered[oldItemPosition].packageName == newApps[newItemPosition].packageName
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                filtered[oldItemPosition] == newApps[newItemPosition]
        }
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        apps = newApps
        filtered = newApps
        diffResult.dispatchUpdatesTo(this)
    }

    fun getFirstItem(): AppInfo? = filtered.firstOrNull()
}

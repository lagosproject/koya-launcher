package com.lagosproject.koya

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.round

data class WidgetInfo(
    val providerInfo: AppWidgetProviderInfo,
    val label: String,
    val appName: String,
    val appIcon: Drawable?,
    val sizeText: String,
    val normalizedLabel: String,
    val normalizedAppName: String
)

class WidgetListAdapter(
    private var widgets: List<WidgetInfo>,
    private val coroutineScope: CoroutineScope,
    private val onWidgetClick: (WidgetInfo) -> Unit
) : RecyclerView.Adapter<WidgetListAdapter.WidgetVH>() {

    private var filtered: List<WidgetInfo> = widgets

    inner class WidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvWidgetSize: TextView = view.findViewById(R.id.tvWidgetSize)
        val tvWidgetLabel: TextView = view.findViewById(R.id.tvWidgetLabel)
        val ivWidgetPreview: ImageView = view.findViewById(R.id.ivWidgetPreview)
        val tvNoPreviewPlaceholder: TextView = view.findViewById(R.id.tvNoPreviewPlaceholder)
        var loadJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_widget_picker, parent, false)
        return WidgetVH(view)
    }

    override fun onBindViewHolder(holder: WidgetVH, position: Int) {
        val widget = filtered[position]
        val context = holder.itemView.context

        holder.tvAppName.text = widget.appName
        holder.tvWidgetSize.text = widget.sizeText
        holder.tvWidgetLabel.text = widget.label

        if (widget.appIcon != null) {
            holder.ivAppIcon.setImageDrawable(widget.appIcon)
            holder.ivAppIcon.visibility = View.VISIBLE
        } else {
            holder.ivAppIcon.visibility = View.GONE
        }

        // Cancel any previous loading job for this recycled viewholder
        holder.loadJob?.cancel()
        holder.ivWidgetPreview.setImageDrawable(null)
        holder.tvNoPreviewPlaceholder.visibility = View.GONE

        // Asynchronously load the preview image
        holder.loadJob = coroutineScope.launch {
            val previewDrawable = withContext(Dispatchers.IO) {
                try {
                    widget.providerInfo.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
                } catch (e: Exception) {
                    null
                }
            }

            // Make sure the coroutine wasn't cancelled while fetching
            if (isActive) {
                if (previewDrawable != null) {
                    holder.ivWidgetPreview.setImageDrawable(previewDrawable)
                    holder.ivWidgetPreview.visibility = View.VISIBLE
                    holder.tvNoPreviewPlaceholder.visibility = View.GONE
                } else {
                    holder.ivWidgetPreview.visibility = View.GONE
                    holder.tvNoPreviewPlaceholder.visibility = View.VISIBLE
                }
            }
        }

        holder.itemView.setOnClickListener {
            onWidgetClick(widget)
        }
    }

    override fun getItemCount(): Int = filtered.size

    fun filter(query: String) {
        val normalizedQuery = normalizeText(query)
        val newFiltered = if (normalizedQuery.isBlank()) {
            widgets
        } else {
            widgets.filter {
                it.normalizedLabel.contains(normalizedQuery) ||
                it.normalizedAppName.contains(normalizedQuery)
            }
        }

        val diffCallback = object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = filtered.size
            override fun getNewListSize(): Int = newFiltered.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                filtered[oldItemPosition].providerInfo.provider == newFiltered[newItemPosition].providerInfo.provider &&
                filtered[oldItemPosition].label == newFiltered[newItemPosition].label
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                filtered[oldItemPosition].label == newFiltered[newItemPosition].label &&
                filtered[oldItemPosition].sizeText == newFiltered[newItemPosition].sizeText
        }
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        filtered = newFiltered
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateList(newWidgets: List<WidgetInfo>) {
        val diffCallback = object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = filtered.size
            override fun getNewListSize(): Int = newWidgets.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                filtered[oldItemPosition].providerInfo.provider == newWidgets[newItemPosition].providerInfo.provider &&
                filtered[oldItemPosition].label == newWidgets[newItemPosition].label
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                filtered[oldItemPosition].label == newWidgets[newItemPosition].label &&
                filtered[oldItemPosition].sizeText == newWidgets[newItemPosition].sizeText
        }
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        widgets = newWidgets
        filtered = newWidgets
        diffResult.dispatchUpdatesTo(this)
    }
}

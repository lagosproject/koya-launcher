package com.lagosproject.koya

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.round

class WidgetPickerActivity : AppCompatActivity() {

    private lateinit var adapter: WidgetListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = layoutInflater.inflate(R.layout.activity_widget_picker, null)
        setContentView(root)

        val etSearch = root.findViewById<EditText>(R.id.etSearch)
        val rv = root.findViewById<RecyclerView>(R.id.rvWidgets)

        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        adapter = WidgetListAdapter(
            widgets = emptyList(),
            coroutineScope = lifecycleScope,
            onWidgetClick = { widget ->
                val result = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, widget.providerInfo.provider)
                }
                setResult(Activity.RESULT_OK, result)
                finish()
            }
        )

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Load widget list in background
        lifecycleScope.launch(Dispatchers.IO) {
            val appWidgetManager = AppWidgetManager.getInstance(this@WidgetPickerActivity)
            val providers = try {
                appWidgetManager.installedProviders
            } catch (e: Exception) {
                emptyList()
            }

            val pm = packageManager
            val widgetList = providers.map { info ->
                val appPackage = info.provider.packageName
                val appInfo = try {
                    pm.getApplicationInfo(appPackage, 0)
                } catch (e: Exception) {
                    null
                }
                val appName = if (appInfo != null) pm.getApplicationLabel(appInfo).toString() else appPackage
                val appIcon = appInfo?.loadIcon(pm)
                val label = info.loadLabel(pm)

                val spanX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0) {
                    info.targetCellWidth
                } else {
                    max(1, round(info.minWidth / 70.0).toInt())
                }
                val spanY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0) {
                    info.targetCellHeight
                } else {
                    max(1, round(info.minHeight / 70.0).toInt())
                }
                val sizeText = "${spanX}x${spanY} (${info.minWidth}dp x ${info.minHeight}dp)"

                WidgetInfo(
                    providerInfo = info,
                    label = label,
                    appName = appName,
                    appIcon = appIcon,
                    sizeText = sizeText,
                    normalizedLabel = normalizeText(label),
                    normalizedAppName = normalizeText(appName)
                )
            }.sortedBy { it.appName }

            withContext(Dispatchers.Main) {
                adapter.updateList(widgetList)
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}

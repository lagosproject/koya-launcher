package com.lagosproject.minwidlauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A simple full-screen activity used only for picking a shortcut app.
 * Returns the selected package name + label via onActivityResult.
 */
class AppPickerActivity : AppCompatActivity() {
    private lateinit var adapter: AppListAdapter
    private lateinit var appRepository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = layoutInflater.inflate(R.layout.activity_app_drawer, null)
        setContentView(root)

        val etSearch = root.findViewById<EditText>(R.id.etSearch)
        val rv = root.findViewById<RecyclerView>(R.id.rvApps)

        appRepository = AppRepository(this)
        val textSize = PrefsHelper.loadHomeShortcutTextSize(this).toFloat()
        adapter = AppListAdapter(
            apps = emptyList(),
            textSize = textSize,
            onAppClick = { app ->
                val result = Intent().apply {
                    putExtra("package", app.packageName)
                    putExtra("label", app.label)
                }
                setResult(RESULT_OK, result)
                finish()
            },
            onAppLongClick = { app ->
                // Long press behaves the same as a normal click in picker:
                val result = Intent().apply {
                    putExtra("package", app.packageName)
                    putExtra("label", app.label)
                }
                setResult(RESULT_OK, result)
                finish()
            }
        )

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Background Load and Refresh
        lifecycleScope.launch(Dispatchers.IO) {
            val cachedApps = appRepository.getCachedApps()
            withContext(Dispatchers.Main) {
                if (cachedApps.isNotEmpty()) {
                    adapter.updateList(cachedApps)
                }
            }

            val freshApps = appRepository.loadApps(excludeSelf = false)
            if (freshApps != cachedApps) {
                appRepository.saveCache(freshApps)
                withContext(Dispatchers.Main) {
                    adapter.updateList(freshApps)
                }
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { adapter.filter(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
    }


}

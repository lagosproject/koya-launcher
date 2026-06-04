package com.lagosproject.koya

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagosproject.koya.databinding.ActivityAppDrawerBinding
import kotlin.math.abs
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDrawerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDrawerBinding
    private lateinit var adapter: AppListAdapter
    private lateinit var appRepository: AppRepository
    private var startY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appRepository = AppRepository(this)
        val textSize = PrefsHelper.loadAppDrawerTextSize(this).toFloat()
        adapter = AppListAdapter(
            emptyList(),
            textSize,
            onAppClick = { app ->
                hideKeyboard()
                launchApp(app)
            },
            onAppLongClick = { app ->
                openAppSettings(app)
            }
        )

        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter

        // Background Load and Refresh
        lifecycleScope.launch(Dispatchers.IO) {
            val cachedApps = appRepository.getCachedApps()
            withContext(Dispatchers.Main) {
                if (cachedApps.isNotEmpty()) {
                    adapter.updateList(cachedApps)
                }
            }

            val freshApps = appRepository.loadApps(excludeSelf = true)
            if (freshApps != cachedApps) {
                appRepository.saveCache(freshApps)
                withContext(Dispatchers.Main) {
                    adapter.updateList(freshApps)
                }
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                adapter.filter(query)
                
                // Auto-launch if exactly one app matches the query
                if (query.isNotBlank() && adapter.itemCount == 1) {
                    adapter.getFirstItem()?.let { app ->
                        launchApp(app)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Autofocus search bar and show keyboard
        binding.etSearch.requestFocus()
        binding.etSearch.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> startY = ev.y
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - startY
                // If dragging down more than 200px and we are at the top of the list
                if (dy > 200 && !binding.rvApps.canScrollVertically(-1)) {
                    closeDrawer()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun closeDrawer() {
        hideKeyboard()
        finish()
        overridePendingTransition(0, R.anim.slide_down)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    override fun onBackPressed() {
        hideKeyboard()
        super.onBackPressed()
        overridePendingTransition(0, R.anim.slide_down)
    }

    private fun launchApp(app: AppInfo) {
        packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
            finish()
        }
    }

    private fun openAppSettings(app: AppInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", app.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }


}

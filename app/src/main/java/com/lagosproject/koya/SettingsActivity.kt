package com.lagosproject.koya

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lagosproject.koya.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val RESULT_WIDGET_REMOVE = 100
    }

    private lateinit var binding: ActivitySettingsBinding
    private var currentColor: Int = Color.WHITE
    private var activeShortcutSlot: Int = -1

    private val pickShortcutLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && activeShortcutSlot in 0..3) {
            val pkg = result.data?.getStringExtra("package") ?: return@registerForActivityResult
            val label = result.data?.getStringExtra("label") ?: pkg
            PrefsHelper.saveShortcut(this, activeShortcutSlot, pkg, label)
            updateShortcutLabels()
        }
        activeShortcutSlot = -1
    }

    private val requestCalendarPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            binding.swCalendarEvents.isChecked = true
        } else {
            binding.swCalendarEvents.isChecked = false
            Toast.makeText(this, getString(R.string.calendar_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load existing values
        val appDrawerSize = PrefsHelper.loadAppDrawerTextSize(this).coerceIn(12, 36)
        binding.sbAppDrawerTextSize.progress = appDrawerSize - 12
        binding.tvAppDrawerTextSizeVal.text = "${appDrawerSize}sp"

        val homeShortcutSize = PrefsHelper.loadHomeShortcutTextSize(this).coerceIn(12, 36)
        binding.sbHomeShortcutTextSize.progress = homeShortcutSize - 12
        binding.tvHomeShortcutTextSizeVal.text = "${homeShortcutSize}sp"

        val widgetHeight = PrefsHelper.loadWidgetHeight(this).coerceIn(100, 600)
        binding.sbWidgetHeight.progress = widgetHeight - 100
        binding.tvWidgetHeightVal.text = "${widgetHeight}dp"

        binding.swBatteryBar.isChecked = PrefsHelper.loadBatteryBarVisible(this)
        binding.swDefaultColors.isChecked = PrefsHelper.loadUseDefaultColors(this)

        currentColor = PrefsHelper.loadCustomTextColor(this)
        updateColorIndicator(currentColor)

        binding.swUsageCounter.isChecked = PrefsHelper.loadShowUsageCounter(this) && hasUsageStatsPermission()
        binding.swCalendarEvents.isChecked = PrefsHelper.loadShowCalendarEvents(this) &&
                (checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED)

        // Setup Seekbar Listeners
        binding.sbAppDrawerTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvAppDrawerTextSizeVal.text = "${progress + 12}sp"
                if (seekBar != null) {
                    updateAppDrawerPreview(seekBar, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                setPreviewMode(seekBar, true)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                setPreviewMode(seekBar, false)
            }
        })

        binding.sbHomeShortcutTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvHomeShortcutTextSizeVal.text = "${progress + 12}sp"
                if (seekBar != null) {
                    updateHomePreview(seekBar, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                setPreviewMode(seekBar, true)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                setPreviewMode(seekBar, false)
            }
        })

        binding.sbWidgetHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvWidgetHeightVal.text = "${progress + 100}dp"
                if (seekBar != null) {
                    updateHomePreview(seekBar, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                setPreviewMode(seekBar, true)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                setPreviewMode(seekBar, false)
            }
        })

        // Setup switch listeners for permission checking
        binding.swUsageCounter.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasUsageStatsPermission()) {
                Toast.makeText(this, getString(R.string.usage_access_required), Toast.LENGTH_LONG).show()
                startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                binding.swUsageCounter.isChecked = false
            }
        }

        binding.swCalendarEvents.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestCalendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
            }
        }

        binding.swDefaultColors.setOnCheckedChangeListener { _, isChecked ->
            updateCustomColorRowState(isChecked)
        }
        updateCustomColorRowState(binding.swDefaultColors.isChecked)

        // Open color picker when tapping custom color row
        binding.rowCustomColor.setOnClickListener {
            showColorPicker()
        }

        // Load shortcut labels
        updateShortcutLabels()

        binding.btnRemoveWidget.setOnClickListener {
            removeWidget()
        }

        binding.btnSetDefault.setOnClickListener {
            openDefaultLauncherSettings()
        }

        // Header back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Shortcut pickers
        val shortcutButtons = arrayOf(
            binding.btnShortcut0,
            binding.btnShortcut1,
            binding.btnShortcut2,
            binding.btnShortcut3
        )

        shortcutButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                pickShortcutApp(index)
            }
        }
    }

    private fun updateShortcutLabels() {
        val shortcutButtons = arrayOf(
            binding.btnShortcut0,
            binding.btnShortcut1,
            binding.btnShortcut2,
            binding.btnShortcut3
        )

        shortcutButtons.forEachIndexed { index, button ->
            val data = PrefsHelper.loadShortcut(this, index)
            if (data != null) {
                val localizedLabel = try {
                    val appInfo = packageManager.getApplicationInfo(data.first, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    data.second
                }
                button.text = localizedLabel
            } else {
                button.text = getString(R.string.empty_shortcut)
            }
        }
    }

    private fun removeWidget() {
        setResult(RESULT_WIDGET_REMOVE)
        finish()
    }

    private fun openDefaultLauncherSettings() {
        val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(android.provider.Settings.ACTION_SETTINGS)
            startActivity(fallback)
        }
    }

    private fun updateCustomColorRowState(useDefaultColors: Boolean) {
        if (useDefaultColors) {
            binding.rowCustomColor.alpha = 0.4f
            binding.rowCustomColor.isClickable = false
            binding.rowCustomColor.isFocusable = false
        } else {
            binding.rowCustomColor.alpha = 1.0f
            binding.rowCustomColor.isClickable = true
            binding.rowCustomColor.isFocusable = true
        }
    }

    private fun updateColorIndicator(color: Int) {
        currentColor = color
        binding.tvCustomColorHex.text = String.format("#%06X", 0xFFFFFF and color)
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, Color.parseColor("#40FFFFFF"))
        }
        binding.viewColorIndicator.background = drawable
    }

    private fun saveSettings() {
        val appDrawerSize = binding.sbAppDrawerTextSize.progress + 12
        val homeShortcutSize = binding.sbHomeShortcutTextSize.progress + 12
        val widgetHeight = binding.sbWidgetHeight.progress + 100
        val batteryVisible = binding.swBatteryBar.isChecked
        
        val useDefaultColors = binding.swDefaultColors.isChecked
        val showUsage = binding.swUsageCounter.isChecked
        val showCalendar = binding.swCalendarEvents.isChecked

        PrefsHelper.saveAppDrawerTextSize(this, appDrawerSize)
        PrefsHelper.saveHomeShortcutTextSize(this, homeShortcutSize)
        PrefsHelper.saveWidgetHeight(this, widgetHeight)
        PrefsHelper.saveBatteryBarVisible(this, batteryVisible)
        
        PrefsHelper.saveUseDefaultColors(this, useDefaultColors)
        PrefsHelper.saveCustomTextColor(this, currentColor)
        
        // Save permission-guarded settings only if permissions are valid
        PrefsHelper.saveShowUsageCounter(this, showUsage && hasUsageStatsPermission())
        PrefsHelper.saveShowCalendarEvents(this, showCalendar &&
                (checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED))
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }

    override fun onResume() {
        super.onResume()
        // Synchronize switch states with actual system permissions in case user changed them in settings
        binding.swUsageCounter.isChecked = PrefsHelper.loadShowUsageCounter(this) && hasUsageStatsPermission()
        binding.swCalendarEvents.isChecked = PrefsHelper.loadShowCalendarEvents(this) &&
                (checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    private fun pickShortcutApp(slot: Int) {
        activeShortcutSlot = slot
        val intent = Intent(this, AppPickerActivity::class.java)
        pickShortcutLauncher.launch(intent)
    }

    private fun hasUsageStatsPermission(): Boolean = PermissionHelper.hasUsageStatsPermission(this)

    private fun showColorPicker() {
        val inflater = layoutInflater
        val view = inflater.inflate(R.layout.dialog_color_picker, null)

        val preview = view.findViewById<View>(R.id.viewPreview)
        val tvHex = view.findViewById<TextView>(R.id.tvHex)
        val seekRed = view.findViewById<SeekBar>(R.id.seekRed)
        val seekGreen = view.findViewById<SeekBar>(R.id.seekGreen)
        val seekBlue = view.findViewById<SeekBar>(R.id.seekBlue)

        val presetWhite = view.findViewById<View>(R.id.presetWhite)
        val presetLightGray = view.findViewById<View>(R.id.presetLightGray)
        val presetGray = view.findViewById<View>(R.id.presetGray)
        val presetAccent = view.findViewById<View>(R.id.presetAccent)

        var r = Color.red(currentColor)
        var g = Color.green(currentColor)
        var b = Color.blue(currentColor)

        fun applyColor(color: Int) {
            r = Color.red(color)
            g = Color.green(color)
            b = Color.blue(color)
            seekRed.progress = r
            seekGreen.progress = g
            seekBlue.progress = b
            val hex = String.format("#%06X", 0xFFFFFF and color)
            preview.setBackgroundColor(color)
            tvHex.text = hex
        }

        seekRed.max = 255
        seekGreen.max = 255
        seekBlue.max = 255

        applyColor(currentColor)

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                when (seekBar?.id) {
                    R.id.seekRed -> r = progress
                    R.id.seekGreen -> g = progress
                    R.id.seekBlue -> b = progress
                }
                val color = Color.rgb(r, g, b)
                preview.setBackgroundColor(color)
                tvHex.text = String.format("#%06X", 0xFFFFFF and color)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekRed.setOnSeekBarChangeListener(listener)
        seekGreen.setOnSeekBarChangeListener(listener)
        seekBlue.setOnSeekBarChangeListener(listener)

        presetWhite.setOnClickListener { applyColor(Color.WHITE) }
        presetLightGray.setOnClickListener { applyColor(android.graphics.Color.parseColor("#CCCCCC")) }
        presetGray.setOnClickListener { applyColor(android.graphics.Color.parseColor("#888888")) }
        presetAccent.setOnClickListener { applyColor(android.graphics.Color.parseColor("#00BCD4")) }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                val color = Color.rgb(r, g, b)
                updateColorIndicator(color)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fadeView(view: View, targetAlpha: Float) {
        view.animate().alpha(targetAlpha).setDuration(200).start()
        view.isClickable = targetAlpha > 0.5f
        view.isFocusable = targetAlpha > 0.5f
    }

    private fun setPreviewMode(activeSeekBar: SeekBar?, isPreviewing: Boolean) {
        if (isPreviewing) {
            // Show preview container
            binding.previewContainer.visibility = View.VISIBLE
            binding.homePreview.rootLayout.setBackgroundColor(Color.TRANSPARENT)
            binding.appDrawerPreview.root.setBackgroundColor(Color.parseColor("#E6000000"))
            if (activeSeekBar == binding.sbAppDrawerTextSize) {
                binding.appDrawerPreview.root.visibility = View.VISIBLE
                binding.homePreview.root.visibility = View.GONE
                updateAppDrawerPreview(activeSeekBar, activeSeekBar.progress)
            } else {
                binding.homePreview.root.visibility = View.VISIBLE
                binding.appDrawerPreview.root.visibility = View.GONE
                updateHomePreview(activeSeekBar, activeSeekBar!!.progress)
            }

            // Set translucent background on root layout
            binding.rootLayout.setBackgroundColor(Color.parseColor("#B0000000"))
            binding.layoutCard.background = null

            // Fade out other elements
            fadeView(binding.headerLayout, 0f)
            fadeView(binding.tvLayoutCategory, 0f)
            fadeView(binding.tvColorsCategory, 0f)
            fadeView(binding.cardColors, 0f)
            fadeView(binding.tvInfoCategory, 0f)
            fadeView(binding.cardInfo, 0f)
            fadeView(binding.tvShortcutsCategory, 0f)
            fadeView(binding.cardShortcuts, 0f)
            fadeView(binding.btnRemoveWidget, 0f)
            fadeView(binding.btnSetDefault, 0f)

            // Fade out other controls inside LayoutCard
            if (activeSeekBar == binding.sbAppDrawerTextSize) {
                fadeView(binding.rowHomeShortcutTextSize, 0f)
                fadeView(binding.sbHomeShortcutTextSize, 0f)
                fadeView(binding.rowWidgetHeight, 0f)
                fadeView(binding.sbWidgetHeight, 0f)
                fadeView(binding.rowBatteryIndicator, 0f)
            } else if (activeSeekBar == binding.sbHomeShortcutTextSize) {
                fadeView(binding.rowAppDrawerTextSize, 0f)
                fadeView(binding.sbAppDrawerTextSize, 0f)
                fadeView(binding.rowWidgetHeight, 0f)
                fadeView(binding.sbWidgetHeight, 0f)
                fadeView(binding.rowBatteryIndicator, 0f)
            } else if (activeSeekBar == binding.sbWidgetHeight) {
                fadeView(binding.rowAppDrawerTextSize, 0f)
                fadeView(binding.sbAppDrawerTextSize, 0f)
                fadeView(binding.rowHomeShortcutTextSize, 0f)
                fadeView(binding.sbHomeShortcutTextSize, 0f)
                fadeView(binding.rowBatteryIndicator, 0f)
            }
        } else {
            // Hide preview container
            binding.previewContainer.visibility = View.GONE
            binding.homePreview.root.visibility = View.GONE
            binding.appDrawerPreview.root.visibility = View.GONE

            // Restore root layout solid background
            binding.rootLayout.setBackgroundColor(Color.BLACK)
            binding.layoutCard.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.settings_card)

            // Fade in all elements
            fadeView(binding.headerLayout, 1f)
            fadeView(binding.tvLayoutCategory, 1f)
            fadeView(binding.tvColorsCategory, 1f)
            fadeView(binding.cardColors, 1f)
            fadeView(binding.tvInfoCategory, 1f)
            fadeView(binding.cardInfo, 1f)
            fadeView(binding.tvShortcutsCategory, 1f)
            fadeView(binding.cardShortcuts, 1f)
            fadeView(binding.btnRemoveWidget, 1f)
            fadeView(binding.btnSetDefault, 1f)

            // Restore all seekbars and rows inside LayoutCard
            fadeView(binding.rowAppDrawerTextSize, 1f)
            fadeView(binding.sbAppDrawerTextSize, 1f)
            fadeView(binding.rowHomeShortcutTextSize, 1f)
            fadeView(binding.sbHomeShortcutTextSize, 1f)
            fadeView(binding.rowWidgetHeight, 1f)
            fadeView(binding.sbWidgetHeight, 1f)
            fadeView(binding.rowBatteryIndicator, 1f)
        }
    }

    private fun updateHomePreview(activeSlider: SeekBar?, progress: Int) {
        val home = binding.homePreview

        // 1. Time / Date
        val sdfClock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val sdfDate = java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault())
        val now = java.util.Date()
        home.tvClock.text = sdfClock.format(now)
        home.tvDate.text = sdfDate.format(now).replaceFirstChar { it.uppercase() }

        // 2. Battery visible
        home.batteryProgress.visibility = if (binding.swBatteryBar.isChecked) View.VISIBLE else View.GONE
        home.batteryProgress.setProgress(75) // dummy progress for preview

        // 3. Widget height
        val currentWidgetHeight = if (activeSlider == binding.sbWidgetHeight) {
            progress + 100
        } else {
            binding.sbWidgetHeight.progress + 100
        }
        val density = resources.displayMetrics.density
        home.widgetContainer.layoutParams.height = (currentWidgetHeight * density).toInt()
        home.widgetContainer.requestLayout()

        // 4. Shortcuts Text size
        val currentShortcutSize = if (activeSlider == binding.sbHomeShortcutTextSize) {
            (progress + 12).toFloat()
        } else {
            (binding.sbHomeShortcutTextSize.progress + 12).toFloat()
        }
        val shortcuts = arrayOf(home.shortcut0, home.shortcut1, home.shortcut2, home.shortcut3)
        shortcuts.forEachIndexed { index, tv ->
            val data = PrefsHelper.loadShortcut(this, index)
            if (data != null) {
                val localizedLabel = try {
                    val appInfo = packageManager.getApplicationInfo(data.first, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    data.second
                }
                tv.text = localizedLabel
            } else {
                tv.text = getString(R.string.empty_shortcut)
            }
            tv.textSize = currentShortcutSize
        }

        // 5. Colors
        val useDefaultColors = binding.swDefaultColors.isChecked
        val textColor = if (useDefaultColors) Color.WHITE else currentColor

        home.tvClock.setTextColor(textColor)
        home.tvDate.setTextColor(textColor)
        home.tvUsage.setTextColor(textColor)
        home.tvNextEvent.setTextColor(textColor)
        shortcuts.forEach { it.setTextColor(textColor) }
        home.batteryProgress.setIndicatorColor(textColor)
        val trackColor = (textColor and 0x00FFFFFF) or (0x33 shl 24)
        home.batteryProgress.setTrackColor(trackColor)

        // Highlight widget container specifically when adjusting widget height
        if (activeSlider == binding.sbWidgetHeight) {
            home.widgetContainer.setBackgroundColor(Color.parseColor("#80FF0000")) // Semi-transparent red
            home.tvWidgetHint.setTextColor(Color.WHITE)
            home.tvWidgetHint.text = "WIDGET PREVIEW AREA\nHeight: ${currentWidgetHeight}dp"
            home.tvWidgetHint.visibility = View.VISIBLE
        } else {
            home.widgetContainer.setBackgroundResource(R.drawable.widget_bg)
            home.tvWidgetHint.setTextColor(textColor)
            home.tvWidgetHint.text = getString(R.string.widget_hint)
            home.tvWidgetHint.visibility = if (PrefsHelper.loadWidgetId(this) == -1) View.VISIBLE else View.GONE
        }

        // 6. Usage and Calendar visibility
        home.tvUsage.visibility = if (binding.swUsageCounter.isChecked) View.VISIBLE else View.GONE
        home.tvUsage.text = "2h 45m"

        home.tvNextEvent.visibility = if (binding.swCalendarEvents.isChecked) View.VISIBLE else View.GONE
        home.tvNextEvent.text = "12:00 - Next Calendar Event"
    }

    private fun updateAppDrawerPreview(activeSlider: SeekBar?, progress: Int) {
        val drawer = binding.appDrawerPreview

        // Disable search input touch focus in preview
        drawer.etSearch.isFocusable = false
        drawer.etSearch.isFocusableInTouchMode = false

        // Show mock apps in the RecyclerView
        val currentTextSize = if (activeSlider == binding.sbAppDrawerTextSize) {
            (progress + 12).toFloat()
        } else {
            (binding.sbAppDrawerTextSize.progress + 12).toFloat()
        }

        val dummyApps = listOf(
            AppInfo("Browser", "com.android.chrome"),
            AppInfo("Calendar", "com.android.calendar"),
            AppInfo("Camera", "com.android.camera"),
            AppInfo("Messages", "com.android.mms"),
            AppInfo("Phone", "com.android.phone"),
            AppInfo("Settings", "com.android.settings")
        )

        val dummyAdapter = AppListAdapter(
            dummyApps,
            currentTextSize,
            onAppClick = {},
            onAppLongClick = {}
        )
        drawer.rvApps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        drawer.rvApps.adapter = dummyAdapter
    }
}


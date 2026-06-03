package com.lagosproject.minwidlauncher

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
import com.lagosproject.minwidlauncher.databinding.ActivitySettingsBinding

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
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.sbHomeShortcutTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvHomeShortcutTextSizeVal.text = "${progress + 12}sp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.sbWidgetHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvWidgetHeightVal.text = "${progress + 100}dp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
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
            button.text = data?.second ?: getString(R.string.empty_shortcut)
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
}

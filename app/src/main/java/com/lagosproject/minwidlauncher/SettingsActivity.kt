package com.lagosproject.minwidlauncher

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
        private const val REQUEST_SHORTCUT_BASE = 300
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load existing values
        binding.etAppDrawerTextSize.setText(PrefsHelper.loadAppDrawerTextSize(this).toString())
        binding.etHomeShortcutTextSize.setText(PrefsHelper.loadHomeShortcutTextSize(this).toString())
        binding.etWidgetHeight.setText(PrefsHelper.loadWidgetHeight(this).toString())
        binding.swBatteryBar.isChecked = PrefsHelper.loadBatteryBarVisible(this)
        
        binding.swDefaultColors.isChecked = PrefsHelper.loadUseDefaultColors(this)
        val customColor = PrefsHelper.loadCustomTextColor(this)
        currentColor = customColor
        binding.etCustomColor.setText(String.format("#%06X", 0xFFFFFF and customColor))
        binding.swUsageCounter.isChecked = PrefsHelper.loadShowUsageCounter(this)
        binding.swCalendarEvents.isChecked = PrefsHelper.loadShowCalendarEvents(this)

        // Open color picker when tapping custom color
        binding.etCustomColor.setOnClickListener {
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

        binding.btnSave.setOnClickListener {
            saveSettings()
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
        val intent = android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback for older Android versions
            val fallback = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
            startActivity(fallback)
        }
    }

    private fun saveSettings() {
        val appDrawerSize = (binding.etAppDrawerTextSize.text.toString().toIntOrNull() ?: 22).coerceIn(12, 48)
        val homeShortcutSize = (binding.etHomeShortcutTextSize.text.toString().toIntOrNull() ?: 18).coerceIn(12, 48)
        val widgetHeight = (binding.etWidgetHeight.text.toString().toIntOrNull() ?: 350).coerceIn(100, 800)
        val batteryVisible = binding.swBatteryBar.isChecked
        
        val useDefaultColors = binding.swDefaultColors.isChecked
        val colorHex = binding.etCustomColor.text.toString()
        val customColor = try { Color.parseColor(colorHex) } catch (e: Exception) { currentColor }
        val showUsage = binding.swUsageCounter.isChecked
        val showCalendar = binding.swCalendarEvents.isChecked

        if (showUsage && !hasUsageStatsPermission()) {
            Toast.makeText(this, getString(R.string.usage_access_required), Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }

        if (showCalendar && checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.calendar_permission_required), Toast.LENGTH_LONG).show()
            requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), 124)
            return
        }

        PrefsHelper.saveAppDrawerTextSize(this, appDrawerSize)
        PrefsHelper.saveHomeShortcutTextSize(this, homeShortcutSize)
        PrefsHelper.saveWidgetHeight(this, widgetHeight)
        PrefsHelper.saveBatteryBarVisible(this, batteryVisible)
        
        PrefsHelper.saveUseDefaultColors(this, useDefaultColors)
        PrefsHelper.saveCustomTextColor(this, customColor)
        PrefsHelper.saveShowUsageCounter(this, showUsage)
        PrefsHelper.saveShowCalendarEvents(this, showCalendar)

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        
        // Return to HomeActivity
        setResult(RESULT_OK)
        finish()
    }

    private fun pickShortcutApp(slot: Int) {
        activeShortcutSlot = slot
        val intent = Intent(this, AppPickerActivity::class.java)
        pickShortcutLauncher.launch(intent)
    }

    // Legacy onActivityResult removed - replaced by Activity Result Launcher

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

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

        // Initialize sliders and preview
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

        // Preset taps
        presetWhite.setOnClickListener { applyColor(Color.WHITE) }
        presetLightGray.setOnClickListener { applyColor(Color.parseColor("#CCCCCC")) }
        presetGray.setOnClickListener { applyColor(Color.parseColor("#888888")) }
        presetAccent.setOnClickListener { applyColor(Color.parseColor("#00BCD4")) }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                val color = Color.rgb(r, g, b)
                currentColor = color
                binding.etCustomColor.setText(String.format("#%06X", 0xFFFFFF and color))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

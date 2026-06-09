package com.lagosproject.koya

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.*
import android.content.pm.PackageManager
import android.app.AppOpsManager
import android.app.WallpaperManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageStats
import android.graphics.Rect
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lagosproject.koya.databinding.ActivityHomeBinding
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import android.widget.Button
import android.widget.ViewFlipper

class HomeActivity : AppCompatActivity() {

    companion object {
        const val WIDGET_HOST_ID = 1024
        const val REQUEST_PICK_APPWIDGET = 200
        const val REQUEST_CREATE_APPWIDGET = 201
        const val REQUEST_SETTINGS = 202
    }

    private lateinit var binding: ActivityHomeBinding
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var gestureDetector: GestureDetector

    private val pickShortcutLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && pendingSlot in 0..3) {
            val pkg = result.data?.getStringExtra("package") ?: return@registerForActivityResult
            val label = result.data?.getStringExtra("label") ?: pkg
            PrefsHelper.saveShortcut(this, pendingSlot, pkg, label)
            setupShortcuts()
        }
        pendingSlot = -1
    }

    private val pickWidgetLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val appWidgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            val component = result.data?.getParcelableExtra<ComponentName>(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
            if (appWidgetId != -1 && component != null) {
                bindAndConfigureWidget(appWidgetId, component)
            } else {
                if (pendingWidgetId != -1) {
                    appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                    pendingWidgetId = -1
                }
            }
        } else {
            if (pendingWidgetId != -1) {
                appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                pendingWidgetId = -1
            }
        }
    }

    private val bindWidgetLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        val comp = pendingWidgetComponent
        if (result.resultCode == RESULT_OK && id != -1 && comp != null) {
            val success = appWidgetManager.bindAppWidgetIdIfAllowed(id, comp)
            if (success) {
                val appWidgetInfo = appWidgetManager.getAppWidgetInfo(id)
                if (appWidgetInfo?.configure != null) {
                    val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = appWidgetInfo.configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    }
                    try {
                        createWidgetLauncher.launch(configIntent)
                    } catch (e: Exception) {
                        Log.e("MinimalLauncher", "Failed to start config activity after bind", e)
                        appWidgetHost.deleteAppWidgetId(id)
                        pendingWidgetId = -1
                    }
                } else {
                    hostWidget(id)
                    pendingWidgetId = -1
                }
            } else {
                appWidgetHost.deleteAppWidgetId(id)
                pendingWidgetId = -1
            }
        } else {
            if (id != -1) {
                appWidgetHost.deleteAppWidgetId(id)
                pendingWidgetId = -1
            }
        }
        pendingWidgetComponent = null
    }

    private fun bindAndConfigureWidget(appWidgetId: Int, component: ComponentName) {
        val success = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, component)
        if (success) {
            val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            if (appWidgetInfo?.configure != null) {
                pendingWidgetId = appWidgetId
                val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    this.component = appWidgetInfo.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                try {
                    createWidgetLauncher.launch(configIntent)
                } catch (e: Exception) {
                    Log.e("MinimalLauncher", "Failed to start config activity", e)
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    pendingWidgetId = -1
                }
            } else {
                hostWidget(appWidgetId)
                pendingWidgetId = -1
            }
        } else {
            pendingWidgetId = appWidgetId
            pendingWidgetComponent = component
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component)
            }
            bindWidgetLauncher.launch(intent)
        }
    }

    private val createWidgetLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intentId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        val id = if (intentId != -1) intentId else pendingWidgetId
        if (result.resultCode == RESULT_OK && id != -1) {
            hostWidget(id)
        } else {
            if (id != -1) appWidgetHost.deleteAppWidgetId(id)
        }
        pendingWidgetId = -1
    }

    private val settingsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == SettingsActivity.RESULT_WIDGET_REMOVE) {
            val oldId = PrefsHelper.loadWidgetId(this)
            if (oldId != -1) {
                appWidgetHost.deleteAppWidgetId(oldId)
                binding.widgetContainer.removeAllViews()
                PrefsHelper.clearWidget(this)
                updateWidgetHintVisibility()
                Log.d("MinimalLauncher", "Widget $oldId removed via settings")
            }
        }
    }

    private val requestCalendarPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            updateNextEvent()
            try {
                startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALENDAR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) { /* ignore */ }
        } else {
            Toast.makeText(this, getString(R.string.calendar_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    private val dateHandler = Handler(Looper.getMainLooper())
    private val dateRunnable = object : Runnable {
        override fun run() {
            binding.tvDate.text = dateFormat.format(Date()).replaceFirstChar { it.uppercase() }
            if (PrefsHelper.loadShowUsageCounter(this@HomeActivity)) {
                updateUsageCounter()
            }
            if (PrefsHelper.loadShowCalendarEvents(this@HomeActivity)) {
                updateNextEvent()
            }
            dateHandler.postDelayed(this, 60_000)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                val pct = (level * 100 / scale)
                binding.batteryProgress.setProgress(pct)
            }
        }
    }

    private val shortcutViews: Array<TextView> by lazy {
        arrayOf(binding.shortcut0, binding.shortcut1, binding.shortcut2, binding.shortcut3)
    }

    // Slot index being replaced (set when a long-press triggers the picker)
    private var pendingSlot = -1

    // Widget ID currently being configured
    private var pendingWidgetId = -1
    private var pendingWidgetComponent: ComponentName? = null
    private var holdAnimator: ValueAnimator? = null
    private var maxHoldProgressAlpha = 0.35f

    private var wallpaperColorListener: WallpaperManager.OnColorsChangedListener? = null
    
    private var isTouchInWidget = false

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent != null && intent.hasExtra("screenshot_mode")) {
            val mode = intent.getBooleanExtra("screenshot_mode", false)
            PrefsHelper.saveScreenshotMode(this, mode)
        }

        appWidgetHost = AppWidgetHost(this, WIDGET_HOST_ID)
        appWidgetManager = AppWidgetManager.getInstance(this)

        setupClickListeners()
        setupShortcuts()
        setupGestureDetector()
        setupWidgetArea()

        // Show tutorial on first launch (skip if in screenshot mode)
        if (!PrefsHelper.loadTutorialShown(this) && !PrefsHelper.loadScreenshotMode(this)) {
            showTutorialDialog()
            PrefsHelper.saveTutorialShown(this, true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("screenshot_mode")) {
            val mode = intent.getBooleanExtra("screenshot_mode", false)
            PrefsHelper.saveScreenshotMode(this, mode)
            applyLayoutSettings()
        }
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        dateHandler.post(dateRunnable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setupWallpaperColorListener()
        }
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
        unregisterReceiver(batteryReceiver)
        dateHandler.removeCallbacks(dateRunnable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            unregisterWallpaperColorListener()
        }
    }

    override fun onResume() {
        super.onResume()
        applyLayoutSettings()
        setupShortcuts()
    }

    private fun applyLayoutSettings() {
        binding.rootLayout.setBackgroundColor(Color.TRANSPARENT)
        updateWidgetHintVisibility()

        // Battery Indicator - Updated to target batteryProgress directly
        binding.batteryProgress.visibility = if (PrefsHelper.loadBatteryBarVisible(this)) View.VISIBLE else View.GONE
        
        // Widget Height
        val heightDp = PrefsHelper.loadWidgetHeight(this)
        val density = resources.displayMetrics.density
        binding.widgetContainer.layoutParams.height = (heightDp * density).toInt()
        binding.widgetContainer.requestLayout()
        
        // Shortcut Text Sizes
        val shortcutSize = PrefsHelper.loadHomeShortcutTextSize(this).toFloat()
        shortcutViews.forEach { it.textSize = shortcutSize }

        // Colors
        if (!PrefsHelper.loadUseDefaultColors(this)) {
            val customColor = PrefsHelper.loadCustomTextColor(this)
            binding.tvClock.setTextColor(customColor)
            binding.tvDate.setTextColor(customColor)
            binding.tvUsage.setTextColor(customColor)
            binding.tvNextEvent.setTextColor(customColor)
            shortcutViews.forEach { it.setTextColor(customColor) }
            // Sync battery indicator with custom text color
            binding.batteryProgress.setIndicatorColor(customColor)
            val trackColor = (customColor and 0x00FFFFFF) or (0x33 shl 24)
            binding.batteryProgress.setTrackColor(trackColor)
            binding.tvWidgetHint.setTextColor(customColor)

            // Dynamic progress tint & opacity
            binding.widgetHoldProgressView.background?.setTint(customColor)
            val isDarkCustomColor = ColorUtils.calculateLuminance(customColor) < 0.5
            maxHoldProgressAlpha = if (isDarkCustomColor) 0.65f else 0.8f
        } else {
            // Re-apply wallpaper colors if needed (will be handled by the listener naturally, but good to force refresh)
            refreshWallpaperColors()
        }

        // Usage Counter
        val showUsage = PrefsHelper.loadShowUsageCounter(this)
        binding.tvUsage.visibility = if (showUsage) View.VISIBLE else View.GONE
        if (showUsage) {
            updateUsageCounter()
            binding.tvUsage.setOnClickListener {
                if (!hasUsageStatsPermission()) {
                    openUsageAccessSettings()
                } else {
                    openDigitalWellbeing()
                }
            }
        }

        // Calendar Events
        val showCalendar = PrefsHelper.loadShowCalendarEvents(this)
        if (showCalendar) {
            updateNextEvent()
            binding.tvNextEvent.setOnClickListener {
                if (checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    requestCalendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_APP_CALENDAR)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        } else {
            binding.tvNextEvent.visibility = View.GONE
        }
    }

    private fun openDigitalWellbeing() {
        // 1) Prefer Google's Digital Wellbeing app if installed
        try {
            val launch = packageManager.getLaunchIntentForPackage("com.google.android.apps.wellbeing")
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
                return
            }
        } catch (_: Exception) {
            // Ignore and try next option
        }

        // 2) Try the Digital Wellbeing settings action (AOSP)
        try {
            val intent = Intent("android.settings.DIGITAL_WELLBEING_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        } catch (_: Exception) {
            // Ignore and fall through
        }

        // 3) If nothing is available, just inform the user
        Toast.makeText(this, getString(R.string.no_wellbeing_app), Toast.LENGTH_SHORT).show()
    }

    private fun openUsageAccessSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.no_usage_permission_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUsageCounter() {
        if (!hasUsageStatsPermission()) {
            binding.tvUsage.text = getString(R.string.permission_needed)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            var totalTimeMs = 0L
            stats?.forEach { totalTimeMs += it.totalTimeInForeground }

            val hours = totalTimeMs / (1000 * 60 * 60)
            val minutes = (totalTimeMs / (1000 * 60)) % 60
            val usageText = String.format("%dh %dm", hours, minutes)

            withContext(Dispatchers.Main) {
                binding.tvUsage.text = usageText
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean = PermissionHelper.hasUsageStatsPermission(this)

    private fun updateNextEvent() {
        if (!PrefsHelper.loadShowCalendarEvents(this)) {
            binding.tvNextEvent.visibility = View.GONE
            return
        }

        if (checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            binding.tvNextEvent.text = getString(R.string.permission_needed_calendar)
            binding.tvNextEvent.visibility = View.VISIBLE
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, now)
            ContentUris.appendId(builder, now + 7 * 24 * 60 * 60 * 1000L) // Next 7 days

            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN
            )
            val sortOrder = "${CalendarContract.Instances.BEGIN} ASC LIMIT 1"

            var eventText: String? = null
            var showEvent = false

            try {
                contentResolver.query(
                    builder.build(),
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val title = cursor.getString(0)
                        val startTime = cursor.getLong(1)
                        val timeStr = formatEventDateTime(this@HomeActivity, startTime)
                        eventText = "$timeStr - $title"
                        showEvent = true
                    }
                }
            } catch (e: Exception) {
                Log.e("MinimalLauncher", "Error querying calendar", e)
            }

            withContext(Dispatchers.Main) {
                if (showEvent && eventText != null) {
                    binding.tvNextEvent.text = eventText
                    binding.tvNextEvent.visibility = View.VISIBLE
                } else {
                    binding.tvNextEvent.visibility = View.GONE
                }
            }
        }
    }

    private fun formatEventDateTime(context: Context, startTime: Long): String {
        val eventDate = Calendar.getInstance().apply { timeInMillis = startTime }
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = timeFormat.format(Date(startTime))

        return when {
            isSameDay(eventDate, today) -> {
                context.getString(R.string.calendar_today, timeStr)
            }
            isSameDay(eventDate, tomorrow) -> {
                context.getString(R.string.calendar_tomorrow, timeStr)
            }
            else -> {
                val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                dateFormat.format(Date(startTime))
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
               cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pendingWidgetId", pendingWidgetId)
        outState.putInt("pendingSlot", pendingSlot)
        outState.putParcelable("pendingWidgetComponent", pendingWidgetComponent)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        pendingWidgetId = savedInstanceState.getInt("pendingWidgetId", -1)
        pendingSlot = savedInstanceState.getInt("pendingSlot", -1)
        pendingWidgetComponent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState.getParcelable("pendingWidgetComponent", ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            savedInstanceState.getParcelable("pendingWidgetComponent")
        }
    }

    // ── Click Listeners ───────────────────────────────────────────────────

    private fun setupClickListeners() {
        // Clock → Clock app
        binding.tvClock.setOnClickListener {
            try {
                // Preferred: open system alarms screen (may require OEM permission)
                startActivity(
                    Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                // If we don't have permission or no handler, fall back safely
                try {
                    startActivity(
                        Intent(Settings.ACTION_DATE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e2: Exception) {
                    Toast.makeText(this, getString(R.string.no_clock_app), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Date → Calendar
        binding.tvDate.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALENDAR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: ActivityNotFoundException) { /* no calendar app */ }
        }

        // Battery → Battery settings - Updated to target batteryProgress directly
        binding.batteryProgress.setOnClickListener {
            startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ── Shortcuts ─────────────────────────────────────────────────────────

    private fun setupShortcuts() {
        val hideEmpty = PrefsHelper.loadHideEmptyTooltips(this)
        shortcutViews.forEachIndexed { slot, tv ->
            val data = PrefsHelper.loadShortcut(this, slot)
            if (data != null) {
                val localizedLabel = try {
                    val appInfo = packageManager.getApplicationInfo(data.first, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    data.second
                }
                tv.text = localizedLabel
                tv.setOnClickListener { launchPackage(data.first) }
                tv.isClickable = true
                tv.isFocusable = true
                tv.visibility = View.VISIBLE
            } else {
                if (hideEmpty) {
                    tv.text = ""
                    tv.setOnClickListener(null)
                    tv.isClickable = false
                    tv.isFocusable = false
                    tv.visibility = View.INVISIBLE
                } else {
                    tv.text = getString(R.string.empty_shortcut)
                    tv.setOnClickListener { pickApp(slot) }
                    tv.isClickable = true
                    tv.isFocusable = true
                    tv.visibility = View.VISIBLE
                }
            }
            tv.setOnLongClickListener {
                if (data != null || !hideEmpty) {
                    Toast.makeText(this, getString(R.string.change_shortcuts_hint), Toast.LENGTH_SHORT).show()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun pickApp(slot: Int) {
        pendingSlot = slot
        val intent = Intent(this, AppPickerActivity::class.java)
        pickShortcutLauncher.launch(intent)
    }

    private fun launchPackage(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
    }

    // Legacy onActivityResult removed - replaced by Activity Result Launchers

    // ── Swipe Up Gesture ─────────────────────────────────────────────────

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dy = (e2.y) - e1.y
                val dx = (e2.x) - e1.x
                
                // Swipe Up
                if (dy < -SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY && abs(dx) < 300) {
                    openDrawer()
                    return true
                }
                
                // Swipe Left / Right
                if (abs(dx) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY && abs(dy) < 300) {
                    if (dx > 0) {
                        // Swipe Right -> Phone
                        try {
                            startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) { Log.e("MinimalLauncher", "No phone app found", e) }
                    } else {
                        // Swipe Left -> Camera
                        try {
                            startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) { Log.e("MinimalLauncher", "No camera app found", e) }
                    }
                    return true
                }
                
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (!isTouchInWidget) {
                    settingsLauncher.launch(Intent(this@HomeActivity, SettingsActivity::class.java))
                }
            }

            override fun onDown(e: MotionEvent) = true
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val location = IntArray(2)
            binding.widgetContainer.getLocationOnScreen(location)
            val rect = Rect(
                location[0],
                location[1],
                location[0] + binding.widgetContainer.width,
                location[1] + binding.widgetContainer.height
            )
            isTouchInWidget = rect.contains(ev.rawX.toInt(), ev.rawY.toInt())
        }

        if (!isTouchInWidget) {
            gestureDetector.onTouchEvent(ev)
        }
        
        return super.dispatchTouchEvent(ev)
    }

    private fun openDrawer() {
        val intent = Intent(this, AppDrawerActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_up, 0)
    }

    // ── Widget ────────────────────────────────────────────────────────────

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupWidgetArea() {
        val savedId = PrefsHelper.loadWidgetId(this)
        if (savedId != -1) {
            hostWidget(savedId)
        } else {
            updateWidgetHintVisibility()
        }

        var startX = 0f
        var startY = 0f
        var isHolding = false
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop

        val resetHoldProgress = {
            holdAnimator?.cancel()
            binding.widgetHoldProgressView.visibility = View.GONE
            binding.widgetHoldProgressView.scaleX = 0f
            binding.widgetHoldProgressView.scaleY = 0f
            binding.widgetHoldProgressView.alpha = 0f
            binding.tvWidgetHint.alpha = 1f
        }

        val cancelHoldProgress = {
            holdAnimator?.cancel()
            val currentScale = binding.widgetHoldProgressView.scaleX
            val currentAlpha = binding.widgetHoldProgressView.alpha
            val currentHintAlpha = binding.tvWidgetHint.alpha
            holdAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 250
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float
                    binding.widgetHoldProgressView.scaleX = currentScale * value
                    binding.widgetHoldProgressView.scaleY = currentScale * value
                    binding.widgetHoldProgressView.alpha = currentAlpha * value
                    binding.tvWidgetHint.alpha = currentHintAlpha + (1f - currentHintAlpha) * (1f - value)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.widgetHoldProgressView.visibility = View.GONE
                        binding.tvWidgetHint.alpha = 1f
                    }
                })
                start()
            }
        }

        val holdRunnable = Runnable {
            if (pendingWidgetId == -1) {
                binding.widgetContainer.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                resetHoldProgress()
                val appWidgetId = appWidgetHost.allocateAppWidgetId()
                pendingWidgetId = appWidgetId
                val pickIntent = Intent(this, WidgetPickerActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                pickWidgetLauncher.launch(pickIntent)
            }
            isHolding = false
        }

        binding.widgetContainer.setOnTouchListener { view, event ->
            val currentId = PrefsHelper.loadWidgetId(this)
            if (currentId != -1) {
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    isHolding = true
                    
                    binding.widgetHoldProgressView.pivotX = event.x
                    binding.widgetHoldProgressView.pivotY = event.y
                    
                    holdAnimator?.cancel()
                    binding.widgetHoldProgressView.visibility = View.VISIBLE
                    binding.widgetHoldProgressView.scaleX = 0f
                    binding.widgetHoldProgressView.scaleY = 0f
                    binding.widgetHoldProgressView.alpha = 0f

                    holdAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 3000
                        interpolator = android.view.animation.LinearInterpolator()
                        addUpdateListener { animation ->
                            val progress = animation.animatedValue as Float
                            binding.widgetHoldProgressView.scaleX = progress
                            binding.widgetHoldProgressView.scaleY = progress
                            binding.widgetHoldProgressView.alpha = progress * maxHoldProgressAlpha
                            binding.tvWidgetHint.alpha = 1f - progress
                        }
                        start()
                    }

                    view.postDelayed(holdRunnable, 3000)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isHolding) {
                        val dx = event.x - startX
                        val dy = event.y - startY
                        if ((dx * dx + dy * dy) > touchSlop * touchSlop) {
                            view.removeCallbacks(holdRunnable)
                            cancelHoldProgress()
                            isHolding = false
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isHolding) {
                        view.removeCallbacks(holdRunnable)
                        cancelHoldProgress()
                        isHolding = false
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun hostWidget(appWidgetId: Int) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            Log.e("MinimalLauncher", "Could not get provider info for widget ID $appWidgetId")
            return
        }
        
        Log.d("MinimalLauncher", "Hosting widget: ID=$appWidgetId, Provider=${info.provider.className}")
        val hostView: AppWidgetHostView = appWidgetHost.createView(applicationContext, appWidgetId, info)
        hostView.setAppWidget(appWidgetId, info)
        binding.widgetContainer.removeAllViews()
        binding.widgetContainer.addView(hostView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        PrefsHelper.saveWidget(this, appWidgetId)
        updateWidgetHintVisibility()
        Log.d("MinimalLauncher", "Widget $appWidgetId hosted successfully")
    }

    private fun updateWidgetHintVisibility() {
        val hasWidget = PrefsHelper.loadWidgetId(this) != -1
        val hideEmpty = PrefsHelper.loadHideEmptyTooltips(this)
        binding.tvWidgetHint.visibility = if (hasWidget || hideEmpty) View.GONE else View.VISIBLE
    }

    // ── Dynamic Colors ───────────────────────────────────────────────────

    private fun setupWallpaperColorListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val wallpaperManager = WallpaperManager.getInstance(this)
            if (wallpaperColorListener == null) {
                val listener = WallpaperManager.OnColorsChangedListener { colors, _ ->
                    updateTextColors(colors)
                }
                wallpaperColorListener = listener
                wallpaperManager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            }
            refreshWallpaperColors()
        }
    }

    private fun refreshWallpaperColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(this)
                val currentColors = wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                updateTextColors(currentColors)
            } catch (e: Exception) { /* ignore */ }
        }
    }

    private fun updateTextColors(colors: android.app.WallpaperColors?) {
        // If custom colors are enabled, don't override them with wallpaper colors
        if (!PrefsHelper.loadUseDefaultColors(this)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // HINT_SUPPORTS_DARK_TEXT was added in API 31 (Android 12)
            val supportsDarkText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                colors?.let {
                    (it.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                } ?: false
            } else {
                false
            }
            
            // Base text color depending on wallpaper brightness
            val baseColor = if (supportsDarkText) Color.BLACK else Color.WHITE
            val primaryColor = baseColor
            val secondaryColor = ColorUtils.setAlphaComponent(baseColor, 0xCC) // ~80%
            val hintColor = ColorUtils.setAlphaComponent(baseColor, 0x80)      // ~50%

            binding.tvClock.setTextColor(primaryColor)
            binding.tvDate.setTextColor(secondaryColor)
            binding.tvUsage.setTextColor(secondaryColor)
            binding.tvNextEvent.setTextColor(hintColor)
            binding.batteryProgress.setIndicatorColor(secondaryColor)
            val trackColor = ColorUtils.setAlphaComponent(baseColor, 0x33)
            binding.batteryProgress.setTrackColor(trackColor)
            binding.tvWidgetHint.setTextColor(hintColor)
            shortcutViews.forEach { it.setTextColor(primaryColor) }

            // Dynamic progress tint & opacity
            val progressColor = if (supportsDarkText) Color.BLACK else Color.WHITE
            binding.widgetHoldProgressView.background?.setTint(progressColor)
            maxHoldProgressAlpha = if (supportsDarkText) 0.65f else 0.8f
        }
    }

    private fun unregisterWallpaperColorListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            wallpaperColorListener?.let {
                WallpaperManager.getInstance(this).removeOnColorsChangedListener(it)
                wallpaperColorListener = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            unregisterWallpaperColorListener()
        }
    }

    // ── Tutorial Dialog ──────────────────────────────────────────────────

    private fun showTutorialDialog() {
        val inflater = layoutInflater
        val view = inflater.inflate(R.layout.dialog_tutorial, null)

        val viewFlipper = view.findViewById<ViewFlipper>(R.id.viewFlipper)
        val dotsContainer = view.findViewById<android.widget.LinearLayout>(R.id.dotsContainer)
        val btnBack = view.findViewById<Button>(R.id.btnTutorialBack)
        val btnNext = view.findViewById<Button>(R.id.btnTutorialNext)
        val tvSkip = view.findViewById<TextView>(R.id.tvTutorialSkip)

        val pageCount = viewFlipper.childCount
        val dots = mutableListOf<View>()

        // Create dot indicators
        for (i in 0 until pageCount) {
            val dot = View(this).apply {
                val size = (8 * resources.displayMetrics.density).toInt()
                val margin = (4 * resources.displayMetrics.density).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, 0, margin, 0)
                }
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (i == 0) Color.WHITE else Color.parseColor("#40FFFFFF"))
                }
                background = drawable
            }
            dots.add(dot)
            dotsContainer.addView(dot)
        }

        fun updatePage(position: Int) {
            dots.forEachIndexed { index, dot ->
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (index == position) Color.WHITE else Color.parseColor("#40FFFFFF"))
                }
                dot.background = drawable
            }
            btnBack.visibility = if (position > 0) View.VISIBLE else View.GONE
            btnNext.text = if (position == pageCount - 1) getString(R.string.tutorial_done) else getString(R.string.tutorial_next)
            tvSkip.visibility = if (position == pageCount - 1) View.GONE else View.VISIBLE
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnNext.setOnClickListener {
            if (viewFlipper.displayedChild < pageCount - 1) {
                viewFlipper.showNext()
                updatePage(viewFlipper.displayedChild)
            } else {
                dialog.dismiss()
            }
        }

        btnBack.setOnClickListener {
            if (viewFlipper.displayedChild > 0) {
                viewFlipper.showPrevious()
                updatePage(viewFlipper.displayedChild)
            }
        }

        tvSkip.setOnClickListener {
            dialog.dismiss()
        }

        updatePage(0)
        dialog.show()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
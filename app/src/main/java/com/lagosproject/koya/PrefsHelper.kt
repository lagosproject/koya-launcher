package com.lagosproject.koya

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object PrefsHelper {

    private const val PREFS_NAME = "minimal_launcher_prefs"
    private const val KEY_WIDGET_ID = "widget_id"
    private const val KEY_WIDGET_PROVIDER = "widget_provider"

    private const val KEY_APP_DRAWER_TEXT_SIZE = "app_drawer_text_size"
    private const val KEY_HOME_SHORTCUT_TEXT_SIZE = "home_shortcut_text_size"
    private const val KEY_BATTERY_BAR_VISIBLE = "battery_bar_visible"
    private const val KEY_WIDGET_HEIGHT_DP = "widget_height_dp"
    private const val KEY_USE_DEFAULT_COLORS = "use_default_colors"
    private const val KEY_CUSTOM_TEXT_COLOR = "custom_text_color"
    private const val KEY_SHOW_USAGE_COUNTER = "show_usage_counter"
    private const val KEY_SHOW_CALENDAR_EVENTS = "show_calendar_events"
    private const val KEY_APP_CACHE = "app_cache"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Shortcuts ──────────────────────────────────────────────────────────

    fun saveShortcut(context: Context, slot: Int, packageName: String, label: String) {
        prefs(context).edit()
            .putString("shortcut_pkg_$slot", packageName)
            .putString("shortcut_label_$slot", label)
            .apply()
    }

    /** Returns Pair(packageName, label) or null if empty. */
    fun loadShortcut(context: Context, slot: Int): Pair<String, String>? {
        val pkg = prefs(context).getString("shortcut_pkg_$slot", null) ?: return null
        val label = prefs(context).getString("shortcut_label_$slot", "+") ?: "+"
        return Pair(pkg, label)
    }

    // ── Widget ─────────────────────────────────────────────────────────────

    fun saveWidget(context: Context, widgetId: Int) {
        prefs(context).edit().putInt(KEY_WIDGET_ID, widgetId).apply()
    }

    fun loadWidgetId(context: Context): Int =
        prefs(context).getInt(KEY_WIDGET_ID, -1)

    fun clearWidget(context: Context) {
        prefs(context).edit()
            .remove(KEY_WIDGET_ID)
            .remove(KEY_WIDGET_PROVIDER)
            .apply()
    }

    // ── Layout Settings ───────────────────────────────────────────────────

    fun saveAppDrawerTextSize(context: Context, size: Int) =
        prefs(context).edit().putInt(KEY_APP_DRAWER_TEXT_SIZE, size).apply()

    fun loadAppDrawerTextSize(context: Context): Int =
        prefs(context).getInt(KEY_APP_DRAWER_TEXT_SIZE, 22)

    fun saveHomeShortcutTextSize(context: Context, size: Int) =
        prefs(context).edit().putInt(KEY_HOME_SHORTCUT_TEXT_SIZE, size).apply()

    fun loadHomeShortcutTextSize(context: Context): Int =
        prefs(context).getInt(KEY_HOME_SHORTCUT_TEXT_SIZE, 18)

    fun saveBatteryBarVisible(context: Context, visible: Boolean) =
        prefs(context).edit().putBoolean(KEY_BATTERY_BAR_VISIBLE, visible).apply()

    fun loadBatteryBarVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BATTERY_BAR_VISIBLE, true)

    fun saveWidgetHeight(context: Context, heightInDp: Int) =
        prefs(context).edit().putInt(KEY_WIDGET_HEIGHT_DP, heightInDp).apply()

    fun loadWidgetHeight(context: Context): Int =
        prefs(context).getInt(KEY_WIDGET_HEIGHT_DP, 350)

    fun saveUseDefaultColors(context: Context, useDefault: Boolean) =
        prefs(context).edit().putBoolean(KEY_USE_DEFAULT_COLORS, useDefault).apply()

    fun loadUseDefaultColors(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_DEFAULT_COLORS, true)

    fun saveCustomTextColor(context: Context, color: Int) =
        prefs(context).edit().putInt(KEY_CUSTOM_TEXT_COLOR, color).apply()

    fun loadCustomTextColor(context: Context): Int =
        prefs(context).getInt(KEY_CUSTOM_TEXT_COLOR, android.graphics.Color.WHITE)

    fun saveShowUsageCounter(context: Context, show: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHOW_USAGE_COUNTER, show).apply()

    fun loadShowUsageCounter(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_USAGE_COUNTER, false)

    fun saveShowCalendarEvents(context: Context, show: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHOW_CALENDAR_EVENTS, show).apply()

    fun loadShowCalendarEvents(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_CALENDAR_EVENTS, false)

    fun saveAppCache(context: Context, apps: List<AppInfo>) {
        val jsonArray = JSONArray()
        apps.forEach { app ->
            val obj = JSONObject()
            obj.put("label", app.label)
            obj.put("pkg", app.packageName)
            jsonArray.put(obj)
        }
        prefs(context).edit().putString(KEY_APP_CACHE, jsonArray.toString()).apply()
    }

    fun loadAppCache(context: Context): List<AppInfo> {
        val json = prefs(context).getString(KEY_APP_CACHE, null) ?: return emptyList()
        val list = mutableListOf<AppInfo>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(AppInfo(obj.getString("label"), obj.getString("pkg")))
            }
        } catch (e: Exception) { /* ignore */ }
        return list
    }

    fun saveScreenshotMode(context: Context, mode: Boolean) =
        prefs(context).edit().putBoolean("screenshot_mode", mode).apply()

    fun loadScreenshotMode(context: Context): Boolean =
        prefs(context).getBoolean("screenshot_mode", false)
}

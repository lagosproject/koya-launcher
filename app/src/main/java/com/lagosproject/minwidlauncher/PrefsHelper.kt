package com.lagosproject.minwidlauncher

import android.content.Context
import android.content.SharedPreferences
import android.appwidget.AppWidgetProviderInfo
import org.json.JSONArray
import org.json.JSONObject

object PrefsHelper {

    private const val PREFS_NAME = "minimal_launcher_prefs"
    private const val KEY_WIDGET_ID = "widget_id"
    private const val KEY_WIDGET_PROVIDER = "widget_provider"

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
        prefs(context).edit().putInt("app_drawer_text_size", size).apply()

    fun loadAppDrawerTextSize(context: Context): Int =
        prefs(context).getInt("app_drawer_text_size", 22)

    fun saveHomeShortcutTextSize(context: Context, size: Int) =
        prefs(context).edit().putInt("home_shortcut_text_size", size).apply()

    fun loadHomeShortcutTextSize(context: Context): Int =
        prefs(context).getInt("home_shortcut_text_size", 18)

    fun saveBatteryBarVisible(context: Context, visible: Boolean) =
        prefs(context).edit().putBoolean("battery_bar_visible", visible).apply()

    fun loadBatteryBarVisible(context: Context): Boolean =
        prefs(context).getBoolean("battery_bar_visible", true)

    fun saveWidgetHeight(context: Context, heightInDp: Int) =
        prefs(context).edit().putInt("widget_height_dp", heightInDp).apply()

    fun loadWidgetHeight(context: Context): Int =
        prefs(context).getInt("widget_height_dp", 350)

    fun saveUseDefaultColors(context: Context, useDefault: Boolean) =
        prefs(context).edit().putBoolean("use_default_colors", useDefault).apply()

    fun loadUseDefaultColors(context: Context): Boolean =
        prefs(context).getBoolean("use_default_colors", true)

    fun saveCustomTextColor(context: Context, color: Int) =
        prefs(context).edit().putInt("custom_text_color", color).apply()

    fun loadCustomTextColor(context: Context): Int =
        prefs(context).getInt("custom_text_color", android.graphics.Color.WHITE)

    fun saveShowUsageCounter(context: Context, show: Boolean) =
        prefs(context).edit().putBoolean("show_usage_counter", show).apply()

    fun loadShowUsageCounter(context: Context): Boolean =
        prefs(context).getBoolean("show_usage_counter", false)

    fun saveShowCalendarEvents(context: Context, show: Boolean) =
        prefs(context).edit().putBoolean("show_calendar_events", show).apply()

    fun loadShowCalendarEvents(context: Context): Boolean =
        prefs(context).getBoolean("show_calendar_events", false)

    fun saveAppCache(context: Context, apps: List<AppInfo>) {
        val jsonArray = JSONArray()
        apps.forEach { app ->
            val obj = JSONObject()
            obj.put("label", app.label)
            obj.put("pkg", app.packageName)
            jsonArray.put(obj)
        }
        prefs(context).edit().putString("app_cache", jsonArray.toString()).apply()
    }

    fun loadAppCache(context: Context): List<AppInfo> {
        val json = prefs(context).getString("app_cache", null) ?: return emptyList()
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
}

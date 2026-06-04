package com.lagosproject.koya

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class AppRepository(private val context: Context) {

    fun getCachedApps(): List<AppInfo> {
        return PrefsHelper.loadAppCache(context)
    }

    fun saveCache(apps: List<AppInfo>) {
        PrefsHelper.saveAppCache(context, apps)
    }

    fun loadApps(excludeSelf: Boolean = false): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val list = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { ri ->
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName
                )
            }
        val filtered = if (excludeSelf) {
            list.filter { it.packageName != context.packageName }
        } else {
            list
        }
        return filtered.sortedBy { it.label.lowercase() }
    }
}

package org.marshsoft.bookreader.data.local

import android.content.Context

class SyncPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    var isSyncEnabled: Boolean
        get() = prefs.getBoolean("is_sync_enabled", false)
        set(value) = prefs.edit().putBoolean("is_sync_enabled", value).apply()

    var isDriveSyncEnabled: Boolean
        get() = prefs.getBoolean("is_drive_sync_enabled", false)
        set(value) = prefs.edit().putBoolean("is_drive_sync_enabled", value).apply()

    var isFirstRun: Boolean
        get() = prefs.getBoolean("is_first_run", true)
        set(value) = prefs.edit().putBoolean("is_first_run", value).apply()
}

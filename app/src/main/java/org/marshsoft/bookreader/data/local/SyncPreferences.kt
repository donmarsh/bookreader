package org.marshsoft.bookreader.data.local

import android.content.Context
import androidx.core.content.edit

class SyncPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    var isSyncEnabled: Boolean
        get() = prefs.getBoolean("is_sync_enabled", false)
        set(value) = prefs.edit { putBoolean("is_sync_enabled", value) }

    var isDriveSyncEnabled: Boolean
        get() = prefs.getBoolean("is_drive_sync_enabled", false)
        set(value) = prefs.edit { putBoolean("is_drive_sync_enabled", value) }

    var isFirstRun: Boolean
        get() = prefs.getBoolean("is_first_run", true)
        set(value) = prefs.edit { putBoolean("is_first_run", value) }

    var readerFontSize: Float
        get() = prefs.getFloat("reader_font_size", 1.0f)
        set(value) = prefs.edit { putFloat("reader_font_size", value) }

    var readerTheme: Int
        // -1: not yet chosen by the user, falls back to the system dark/light setting.
        // 0: Light, 1: Dark, 2: Sepia
        get() = prefs.getInt("reader_theme", -1)
        set(value) = prefs.edit { putInt("reader_theme", value) }

    var librarySortOrder: Int
        get() = prefs.getInt("library_sort_order", 2) // 0: Title, 1: Author, 2: Recently Opened, 3: Recently Added
        set(value) = prefs.edit { putInt("library_sort_order", value) }

    // One-time migration of book files uploaded to Drive's root before the app folder existed.
    // Key is versioned ("_v2") because the first version of this migration used an invalid Drive
    // query, silently found nothing, but still marked itself done - bumping the key makes it
    // retry cleanly on devices that already ran (and were stuck on) the broken version.
    var isDriveFolderOrganized: Boolean
        get() = prefs.getBoolean("is_drive_folder_organized_v2", false)
        set(value) = prefs.edit { putBoolean("is_drive_folder_organized_v2", value) }
}

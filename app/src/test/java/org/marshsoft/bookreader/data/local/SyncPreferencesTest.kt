package org.marshsoft.bookreader.data.local

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncPreferencesTest {

    private lateinit var syncPreferences: SyncPreferences

    @Before
    fun setup() {
        val context = mockk<Context>()
        val fakePrefs = FakeSharedPreferences()
        every { context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE) } returns fakePrefs
        syncPreferences = SyncPreferences(context)
    }

    @Test
    fun isSyncEnabled_defaultsFalse() {
        assertFalse(syncPreferences.isSyncEnabled)
    }

    @Test
    fun isSyncEnabled_persistsValue() {
        syncPreferences.isSyncEnabled = true
        assertTrue(syncPreferences.isSyncEnabled)
    }

    @Test
    fun isDriveSyncEnabled_defaultsFalse() {
        assertFalse(syncPreferences.isDriveSyncEnabled)
    }

    @Test
    fun isDriveSyncEnabled_persistsValue() {
        syncPreferences.isDriveSyncEnabled = true
        assertTrue(syncPreferences.isDriveSyncEnabled)
    }

    @Test
    fun isFirstRun_defaultsTrue() {
        assertTrue(syncPreferences.isFirstRun)
    }

    @Test
    fun isFirstRun_persistsValue() {
        syncPreferences.isFirstRun = false
        assertFalse(syncPreferences.isFirstRun)
    }

    @Test
    fun readerFontSize_defaultsToOne() {
        assertEquals(1.0f, syncPreferences.readerFontSize)
    }

    @Test
    fun readerFontSize_persistsValue() {
        syncPreferences.readerFontSize = 1.5f
        assertEquals(1.5f, syncPreferences.readerFontSize)
    }

    @Test
    fun readerTheme_defaultsToLight() {
        assertEquals(0, syncPreferences.readerTheme)
    }

    @Test
    fun readerTheme_persistsValue() {
        syncPreferences.readerTheme = 1
        assertEquals(1, syncPreferences.readerTheme)
    }

    @Test
    fun librarySortOrder_defaultsToTitle() {
        assertEquals(0, syncPreferences.librarySortOrder)
    }

    @Test
    fun librarySortOrder_persistsValue() {
        syncPreferences.librarySortOrder = 2
        assertEquals(2, syncPreferences.librarySortOrder)
    }

    /**
     * Minimal in-memory SharedPreferences fake so SyncPreferences can be exercised
     * without Robolectric or a real Android environment.
     */
    private class FakeSharedPreferences : SharedPreferences {
        val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values

        override fun getString(key: String?, defValue: String?): String? =
            values[key] as? String ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (values[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                pending[key!!] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                pending[key!!] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                pending[key!!] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                pending[key!!] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                pending[key!!] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                pending[key!!] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                removals.add(key!!)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clear) values.clear()
                removals.forEach { values.remove(it) }
                values.putAll(pending)
            }
        }
    }
}

package org.marshsoft.bookreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import org.marshsoft.bookreader.data.local.dao.BookDao
import org.marshsoft.bookreader.data.local.entities.BookEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN isUploaded INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN lastSyncedTimestamp INTEGER NOT NULL DEFAULT -1")
    }
}

@Database(entities = [BookEntity::class], version = 3)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}

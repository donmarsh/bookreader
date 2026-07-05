package org.marshsoft.bookreader

import android.app.Application
import androidx.room.Room
import org.marshsoft.bookreader.data.local.BookDatabase
import org.marshsoft.bookreader.util.BookParser

class BookReaderApplication : Application() {
    lateinit var database: BookDatabase
    lateinit var bookParser: BookParser

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            BookDatabase::class.java,
            "book_reader_db"
        ).build()
        bookParser = BookParser(this)
    }
}

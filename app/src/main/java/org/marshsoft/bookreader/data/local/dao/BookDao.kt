package org.marshsoft.bookreader.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.marshsoft.bookreader.data.local.entities.BookEntity

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE identifier = :identifier")
    suspend fun getBookByIdentifier(identifier: String): BookEntity?

    @Query("SELECT * FROM books WHERE title = :title AND author = :author")
    suspend fun getBookByTitleAndAuthor(title: String, author: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)
}

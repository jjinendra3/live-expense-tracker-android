package app.liveexpense.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    // Returns -1 when the dedupe key already exists.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPayment(payment: Payment): Long

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun payment(id: Long): Payment?

    @Query("UPDATE payments SET category = :category WHERE id = :id")
    suspend fun setCategory(id: Long, category: String?)

    @Query("SELECT * FROM payments WHERE paidAt >= :from AND paidAt < :until ORDER BY paidAt DESC")
    fun paymentsBetween(from: Long, until: Long): Flow<List<Payment>>

    @Query(
        "SELECT category FROM payments WHERE category IS NOT NULL AND category != :excluded " +
            "GROUP BY category ORDER BY COUNT(*) DESC",
    )
    suspend fun categoriesByUse(excluded: String = Categories.NOT_AN_EXPENSE): List<String>

    @Query("SELECT * FROM payee_memory WHERE payeeKey = :payeeKey")
    suspend fun payeeMemory(payeeKey: String): PayeeMemory?

    @Upsert
    suspend fun savePayeeMemory(memory: PayeeMemory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUnparsed(message: UnparsedMessage): Long

    @Query("SELECT * FROM unparsed_messages ORDER BY receivedAt DESC")
    fun unparsedMessages(): Flow<List<UnparsedMessage>>

    @Query("DELETE FROM unparsed_messages WHERE id = :id")
    suspend fun deleteUnparsed(id: Long)
}

@Database(entities = [Payment::class, PayeeMemory::class, UnparsedMessage::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "live-expense.db").build()
    }
}

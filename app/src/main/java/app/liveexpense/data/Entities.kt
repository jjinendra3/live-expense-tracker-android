package app.liveexpense.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Categories {
    const val NOT_AN_EXPENSE = "Not an expense"
    // Keep these short: they have to fit four across in the prompt.
    val all = listOf("Food", "Grocery", "Travel", "Shop", "Bills", "Fun", "Health", "Other")
}

@Entity(
    tableName = "payments",
    indices = [Index(value = ["dedupeKey"], unique = true), Index(value = ["paidAt"])],
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val payee: String,
    val instrument: String,
    val accountLast: String? = null,
    val reference: String? = null,
    val paidAt: Long,
    val sender: String? = null,
    // Null means uncategorized.
    val category: String? = null,
    val dedupeKey: String? = null,
)

@Entity(tableName = "payee_memory")
data class PayeeMemory(
    @PrimaryKey val payeeKey: String,
    val category: String,
    val confirmations: Int,
)

// Raw text from an allowlisted bank that no rule could read, kept on the phone so format changes show up.
@Entity(
    tableName = "unparsed_messages",
    indices = [Index(value = ["sender", "receivedAt"], unique = true)],
)
data class UnparsedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
)

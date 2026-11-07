package app.liveexpense.data

import java.time.YearMonth
import java.time.ZoneId

// Written by hand because java.text formatters don't do lakh grouping on a plain JVM, so tests and phone would differ.
fun formatRupees(paise: Long): String {
    val rupees = (paise / 100).toString()
    val grouped = if (rupees.length <= 3) {
        rupees
    } else {
        val lakhsAndUp = rupees.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$lakhsAndUp,${rupees.takeLast(3)}"
    }
    val fraction = paise % 100
    return if (fraction == 0L) "₹$grouped" else "₹$grouped.${fraction.toString().padStart(2, '0')}"
}

data class CategoryTotal(val category: String, val paise: Long)

data class MonthSummary(
    val spentPaise: Long,
    val byCategory: List<CategoryTotal>,
    val uncategorizedPaise: Long,
    val uncategorizedCount: Int,
)

fun summarize(payments: List<Payment>): MonthSummary {
    val spending = payments.filter { it.category != Categories.NOT_AN_EXPENSE }
    val uncategorized = spending.filter { it.category == null }
    val byCategory = spending
        .filter { it.category != null }
        .groupBy { it.category!! }
        .map { (category, rows) -> CategoryTotal(category, rows.sumOf { it.amountPaise }) }
        .sortedByDescending { it.paise }
    return MonthSummary(
        spentPaise = spending.sumOf { it.amountPaise },
        byCategory = byCategory,
        uncategorizedPaise = uncategorized.sumOf { it.amountPaise },
        uncategorizedCount = uncategorized.size,
    )
}

fun monthRange(month: YearMonth, zone: ZoneId): Pair<Long, Long> =
    month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

// HSBC messages have no UPI reference, so those fall back to the message's own timestamp.
fun smsDedupeKey(
    reference: String?,
    instrument: String,
    accountLast: String?,
    amountPaise: Long,
    payee: String,
    receivedAt: Long,
): String =
    if (reference != null) "ref:$reference"
    else "sms:$instrument|$accountLast|$amountPaise|$payee|$receivedAt"

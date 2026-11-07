package app.liveexpense.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class MoneyAndTotalsTest {
    private fun payment(amountPaise: Long, category: String?) = Payment(
        amountPaise = amountPaise,
        payee = "Payee",
        instrument = "Cash",
        paidAt = 0L,
        category = category,
    )

    @Test
    fun `whole rupees have no decimals`() {
        assertEquals("₹60", formatRupees(6000))
    }

    @Test
    fun `paise are shown`() {
        assertEquals("₹342.50", formatRupees(34250))
    }

    @Test
    fun `large amounts use Indian grouping`() {
        assertEquals("₹1,20,000", formatRupees(12000000))
    }

    @Test
    fun `spent includes uncategorized payments`() {
        val summary = summarize(listOf(payment(10000, "Food"), payment(5000, null)))
        assertEquals(15000L, summary.spentPaise)
        assertEquals(5000L, summary.uncategorizedPaise)
        assertEquals(1, summary.uncategorizedCount)
    }

    @Test
    fun `not an expense is left out of every total`() {
        val summary = summarize(listOf(payment(10000, "Food"), payment(99900, Categories.NOT_AN_EXPENSE)))
        assertEquals(10000L, summary.spentPaise)
        assertEquals(listOf(CategoryTotal("Food", 10000L)), summary.byCategory)
    }

    @Test
    fun `categories are ordered by amount, largest first`() {
        val summary = summarize(
            listOf(payment(100, "Food"), payment(900, "Bills"), payment(200, "Food")),
        )
        assertEquals(listOf(CategoryTotal("Bills", 900L), CategoryTotal("Food", 300L)), summary.byCategory)
    }

    @Test
    fun `a month runs from its first instant to the first instant of the next`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val range = monthRange(YearMonth.of(2026, 11), zone)
        assertEquals(ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), range.first)
        assertEquals(ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), range.second)
    }

    @Test
    fun `a redelivered message gets the same key as the original`() {
        val first = smsDedupeKey("111122223333", "IDBI Bank", "999", 40000, "A Sharma", receivedAt = 1000L)
        val again = smsDedupeKey("111122223333", "IDBI Bank", "999", 40000, "A Sharma", receivedAt = 2000L)
        assertEquals(first, again)
    }

    @Test
    fun `without a reference, two separate payments at different times stay separate`() {
        val first = smsDedupeKey(null, "HSBC Credit Card", "1111", 34200, "Cafe", receivedAt = 1000L)
        val second = smsDedupeKey(null, "HSBC Credit Card", "1111", 34200, "Cafe", receivedAt = 2000L)
        assertNotEquals(first, second)
    }
}

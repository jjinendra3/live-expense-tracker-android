package app.liveexpense.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Formats copied from real messages. Names, numbers and references are made up: this repo is public.
class SmsParserTest {
    private val parser = SmsParser(BankRules.all)

    private fun parsed(sender: String, body: String): ParsedPayment {
        val result = parser.parse(sender, body)
        return (result as? SmsResult.Payment)?.parsed
            ?: throw AssertionError("expected a payment, got $result")
    }

    private val idbi = "IDBI Bank Acct XX999 debited for Rs 400.00 on 05-Nov-26; " +
        "Bal Rs 1234.32 A Sharma credited. UPI:111122223333. " +
        "To Block UPI send SMS UPIBLOCK <Mob. No> to 07799726511  or call 18002094324-IDBI Bank"

    private val hsbc = "HSBC Credit Card xx1111 used at EXAMPLE FOODS LIMIT for INR 342.00 on 06/11/26. " +
        "Avl limit INR 98765.18; due INR 1234.82. Call +914065118002 to report fraud."

    private val slice = "Rs. 60 spent on your credit card xx2222 at Example cafe on 28-Oct-26 " +
        "(UPI Ref: 444455556666). Not you? Call 080-4832-9999 - slice"

    private val hdfc = "Txn Rs.224.00\nOn HDFC Bank Card 3333\nAt examplecafe.rzp@hdfcb \n" +
        "by UPI 777788889999\nOn 27-08\nNot You?\nCall 18002586161/SMS BLOCK CC 3333 to 7308080808"

    @Test
    fun `IDBI reads the debited amount, not the balance`() {
        assertEquals(40000L, parsed("VM-IDBIBK-S", idbi).amountPaise)
    }

    @Test
    fun `IDBI payee is the person credited, and the word credited does not make it income`() {
        val payment = parsed("VM-IDBIBK-S", idbi)
        assertEquals("A Sharma", payment.payee)
        assertEquals("IDBI", payment.instrument)
        assertEquals("999", payment.accountLast)
        assertEquals("111122223333", payment.reference)
    }

    @Test
    fun `IDBI handles thousands separators in amount and balance`() {
        val body = idbi.replace("Rs 400.00", "Rs 12,400.50").replace("Bal Rs 1234.32", "Bal Rs 1,23,456.32")
        assertEquals(1240050L, parsed("VM-IDBIBK-S", body).amountPaise)
    }

    @Test
    fun `HSBC reads the spend, not the limit or due amount`() {
        val payment = parsed("JD-HSBCIN-S", hsbc)
        assertEquals(34200L, payment.amountPaise)
        assertEquals("EXAMPLE FOODS LIMIT", payment.payee)
        assertEquals("HSBC CC", payment.instrument)
        assertEquals("1111", payment.accountLast)
    }

    @Test
    fun `HSBC has no UPI reference`() {
        assertNull(parsed("JD-HSBCIN-S", hsbc).reference)
    }

    @Test
    fun `slice handles a whole rupee amount and is not fooled by the phone number`() {
        val payment = parsed("JD-SLCBNK-S", slice)
        assertEquals(6000L, payment.amountPaise)
        assertEquals("Example cafe", payment.payee)
        assertEquals("slice CC", payment.instrument)
        assertEquals("2222", payment.accountLast)
        assertEquals("444455556666", payment.reference)
    }

    @Test
    fun `HDFC reads a multi-line message and trims the payee`() {
        val payment = parsed("VM-HDFCBK-S", hdfc)
        assertEquals(22400L, payment.amountPaise)
        assertEquals("examplecafe.rzp@hdfcb", payment.payee)
        assertEquals("HDFC CC", payment.instrument)
        assertEquals("3333", payment.accountLast)
        assertEquals("777788889999", payment.reference)
    }

    @Test
    fun `HDFC handles Windows line endings`() {
        assertEquals(22400L, parsed("VM-HDFCBK-S", hdfc.replace("\n", "\r\n")).amountPaise)
    }

    @Test
    fun `a sender without an operator prefix still works`() {
        assertEquals(40000L, parsed("IDBIBK", idbi).amountPaise)
    }

    @Test
    fun `promotional messages are ignored even when the text matches`() {
        assertEquals(SmsResult.Ignored, parser.parse("VM-IDBIBK-P", idbi))
    }

    @Test
    fun `personal phone numbers are ignored even when the text matches`() {
        assertEquals(SmsResult.Ignored, parser.parse("+919876543210", idbi))
        assertEquals(SmsResult.Ignored, parser.parse("9876543210", idbi))
    }

    @Test
    fun `an OTP from a bank is ignored so its text is never stored`() {
        val otp = "123456 is your OTP for a txn of Rs 500.00 at EXAMPLE. Do not share it. - HDFC Bank"
        assertEquals(SmsResult.Ignored, parser.parse("VM-HDFCBK-T", otp))
    }

    @Test
    fun `a bank message with an amount but no matching rule is unparsed`() {
        val credit = "IDBI Bank Acct XX999 credited with Rs 100.00 on 05-Nov-26. Bal Rs 1234.32"
        assertEquals(SmsResult.Unparsed, parser.parse("VM-IDBIBK-S", credit))
    }

    @Test
    fun `an unreadable message from each bank's real sender is unparsed`() {
        val unknownFormat = "Rs 100.00 refunded to your account."
        listOf("JD-IDBIBK-S", "VM-HDFCBK-S", "JM-HSBCIN-S", "JD-HSBCIM-S", "JD-SLCBNK-S").forEach { sender ->
            assertEquals(sender, SmsResult.Unparsed, parser.parse(sender, unknownFormat))
        }
    }

    @Test
    fun `a bank offer does not prompt and lands in unparsed`() {
        val offer = "Pre-approved personal loan up to Rs 5,00,000. Apply now. - HDFC Bank"
        assertEquals(SmsResult.Unparsed, parser.parse("VM-HDFCBK-S", offer))
    }

    @Test
    fun `a bank message without an amount is ignored`() {
        assertEquals(SmsResult.Ignored, parser.parse("VM-HDFCBK-S", "Your statement is ready. - HDFC Bank"))
    }

    @Test
    fun `a non-bank message with an amount is ignored so its text is never stored`() {
        assertEquals(SmsResult.Ignored, parser.parse("VM-EXMPLE-S", "Your order of Rs 200 is on its way"))
    }
}

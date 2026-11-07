package app.liveexpense.capture

import java.math.BigDecimal

data class ParsedPayment(
    val amountPaise: Long,
    val payee: String,
    val instrument: String,
    val accountLast: String?,
    val reference: String?,
)

sealed interface SmsResult {
    data class Payment(val parsed: ParsedPayment) : SmsResult

    // From a known bank and mentions money, but no rule could read it.
    data object Unparsed : SmsResult

    data object Ignored : SmsResult
}

/**
 * One message format. [pattern] needs named groups `amount`, `payee` and `account`, and may have `reference`.
 * [senderTokens] mark a sender as this bank when deciding whether an unreadable message is worth keeping.
 */
data class BankRule(
    val instrument: String,
    val senderTokens: Set<String>,
    val pattern: Regex,
)

class SmsParser(private val rules: List<BankRule>) {
    fun parse(sender: String, body: String): SmsResult {
        val header = SENDER_HEADER.matchEntire(sender.trim().uppercase()) ?: return SmsResult.Ignored
        val (name, suffix) = header.destructured
        if (name.none(Char::isLetter) || suffix == PROMOTIONAL_SUFFIX) return SmsResult.Ignored

        // Matching on the text, not the sender, so a sender string we guessed wrong can't silently drop a payment.
        for (rule in rules) {
            val match = rule.pattern.find(body) ?: continue
            return SmsResult.Payment(toPayment(rule, match))
        }

        val fromKnownBank = rules.any { rule -> rule.senderTokens.any { it in name } }
        return when {
            !fromKnownBank -> SmsResult.Ignored
            OTP.containsMatchIn(body) -> SmsResult.Ignored
            !MENTIONS_MONEY.containsMatchIn(body) -> SmsResult.Ignored
            else -> SmsResult.Unparsed
        }
    }

    private fun toPayment(rule: BankRule, match: MatchResult) = ParsedPayment(
        amountPaise = toPaise(match.groups["amount"]!!.value),
        payee = match.groups["payee"]!!.value.trim(),
        instrument = rule.instrument,
        accountLast = match.groups["account"]!!.value.filter(Char::isDigit).ifEmpty { null },
        reference = referenceOrNull(match),
    )

    // Asking a regex for a group it doesn't define throws, and not every format has a reference.
    private fun referenceOrNull(match: MatchResult): String? =
        try {
            match.groups["reference"]?.value
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun toPaise(amount: String): Long =
        BigDecimal(amount.replace(",", "")).movePointRight(2).longValueExact()

    private companion object {
        // Optional operator prefix, the sender name, then an optional category suffix: VM-HDFCBK-S.
        val SENDER_HEADER = Regex("""^(?:[A-Z0-9]{2}-)?([A-Z0-9]{3,})(?:-([A-Z]))?$""")
        const val PROMOTIONAL_SUFFIX = "P"
        val OTP = Regex("""\b(OTP|one[- ]time password|verification code)\b""", RegexOption.IGNORE_CASE)
        val MENTIONS_MONEY = Regex("""(?:Rs\.?|INR|₹)\s*\d""", RegexOption.IGNORE_CASE)
    }
}

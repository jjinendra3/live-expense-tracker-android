package app.liveexpense.data

import app.liveexpense.capture.ParsedPayment
import app.liveexpense.capture.SmsParser
import app.liveexpense.capture.SmsResult
import app.liveexpense.prompt.PromptDecision
import app.liveexpense.prompt.decidePrompt
import app.liveexpense.prompt.payeeKey
import app.liveexpense.prompt.promptCategories
import app.liveexpense.prompt.rememberChoice

data class PendingPrompt(
    val payment: Payment,
    val suggested: String?,
    val categories: List<String>,
)

class Expenses(private val dao: ExpenseDao, private val parser: SmsParser) {
    /** Saves whatever the message contains and returns a prompt if the user needs asking. */
    suspend fun recordSms(sender: String, body: String, receivedAt: Long): PendingPrompt? =
        when (val result = parser.parse(sender, body)) {
            SmsResult.Ignored -> null
            SmsResult.Unparsed -> {
                dao.insertUnparsed(UnparsedMessage(sender = sender, body = body, receivedAt = receivedAt))
                null
            }
            is SmsResult.Payment -> recordPayment(result.parsed, sender, receivedAt)
        }

    private suspend fun recordPayment(parsed: ParsedPayment, sender: String, receivedAt: Long): PendingPrompt? {
        val decision = decidePrompt(dao.payeeMemory(payeeKey(parsed.payee)))
        val payment = Payment(
            amountPaise = parsed.amountPaise,
            payee = parsed.payee,
            instrument = parsed.instrument,
            accountLast = parsed.accountLast,
            reference = parsed.reference,
            paidAt = receivedAt,
            sender = sender,
            category = (decision as? PromptDecision.AutoFile)?.category,
            dedupeKey = smsDedupeKey(
                parsed.reference,
                parsed.instrument,
                parsed.accountLast,
                parsed.amountPaise,
                parsed.payee,
                receivedAt,
            ),
        )
        val id = dao.insertPayment(payment)
        val isDuplicate = id == -1L
        if (isDuplicate || decision !is PromptDecision.Ask) return null

        return PendingPrompt(
            payment = payment.copy(id = id),
            suggested = decision.suggested,
            categories = promptCategories(decision.suggested, dao.categoriesByUse()),
        )
    }

    /** Returns the payment as saved, or null if it no longer exists. */
    suspend fun categorize(paymentId: Long, category: String): Payment? {
        val payment = dao.payment(paymentId) ?: return null
        dao.setCategory(paymentId, category)
        val key = payeeKey(payment.payee)
        dao.savePayeeMemory(rememberChoice(dao.payeeMemory(key), key, category))
        return payment.copy(category = category)
    }

    suspend fun addManual(amountPaise: Long, payee: String, category: String?, paidAt: Long) {
        dao.insertPayment(
            Payment(
                amountPaise = amountPaise,
                payee = payee,
                instrument = "Cash",
                paidAt = paidAt,
                category = category,
            ),
        )
    }
}

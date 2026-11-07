package app.liveexpense.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.liveexpense.LiveExpenseApp
import app.liveexpense.prompt.PaymentPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        val first = parts.firstOrNull() ?: return
        val sender = first.originatingAddress ?: return
        // Long messages, like IDBI's, arrive split across several parts.
        val body = parts.joinToString("") { it.messageBody.orEmpty() }

        val app = context.applicationContext as LiveExpenseApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prompt = app.expenses.recordSms(sender, body, first.timestampMillis)
                if (prompt != null) {
                    delay(PROMPT_DELAY_MS)
                    PaymentPrompt.show(app, prompt)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        // The SMS app posts its own banner for the same message, and a banner can cover ours. Going last keeps ours up.
        const val PROMPT_DELAY_MS = 2_000L
    }
}

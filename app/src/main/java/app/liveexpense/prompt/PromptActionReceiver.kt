package app.liveexpense.prompt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.liveexpense.LiveExpenseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PromptActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val paymentId = intent.getLongExtra(EXTRA_PAYMENT_ID, -1)
        val category = intent.getStringExtra(EXTRA_CATEGORY)
        if (paymentId == -1L || category == null) return

        val app = context.applicationContext as LiveExpenseApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val saved = app.expenses.categorize(paymentId, category)
                if (saved != null) {
                    PaymentPrompt.showConfirmation(app, saved)
                } else {
                    NotificationManagerCompat.from(app).cancel(PaymentPrompt.NOTIFICATION_ID)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PAYMENT_ID = "payment_id"
        const val EXTRA_CATEGORY = "category"
    }
}

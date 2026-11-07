package app.liveexpense.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.liveexpense.LiveExpenseApp
import app.liveexpense.prompt.PaymentPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// A real phone can't be sent a test SMS from adb, so this runs made-up text through the same flow as SmsReceiver.
class FakeSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender")
        val body = intent.getStringExtra("body")
        val clear = intent.getBooleanExtra("clear", false)

        val app = context.applicationContext as LiveExpenseApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (clear) clearTestPayments(app)
                if (sender != null && body != null) {
                    val prompt = app.expenses.recordSms(sender, body, System.currentTimeMillis())
                    if (prompt != null) PaymentPrompt.show(app, prompt)
                }
            } finally {
                pending.finish()
            }
        }
    }

    // Removes only payments whose UPI reference has the test prefix, so real payments stay.
    private fun clearTestPayments(app: LiveExpenseApp) {
        val db = app.database.openHelper.writableDatabase
        db.execSQL(
            "DELETE FROM payee_memory WHERE payeeKey IN " +
                "(SELECT lower(trim(payee)) FROM payments WHERE reference LIKE ?)",
            arrayOf(TEST_REFERENCE_LIKE),
        )
        val removed = db.delete("payments", "reference LIKE ?", arrayOf(TEST_REFERENCE_LIKE))
        Log.i(TAG, "removed $removed test payments")
    }

    private companion object {
        const val TAG = "FakeSmsReceiver"
        const val TEST_REFERENCE_LIKE = "11112222%"
    }
}

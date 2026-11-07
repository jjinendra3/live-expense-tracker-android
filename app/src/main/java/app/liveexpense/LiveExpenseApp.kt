package app.liveexpense

import android.app.Application
import app.liveexpense.capture.BankRules
import app.liveexpense.capture.SmsParser
import app.liveexpense.data.AppDatabase
import app.liveexpense.data.Expenses
import app.liveexpense.prompt.PaymentPrompt

class LiveExpenseApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val expenses by lazy { Expenses(database.expenseDao(), SmsParser(BankRules.all)) }

    override fun onCreate() {
        super.onCreate()
        PaymentPrompt.createChannels(this)
    }
}

package app.liveexpense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.liveexpense.LiveExpenseApp
import app.liveexpense.data.Payment
import app.liveexpense.data.UnparsedMessage
import app.liveexpense.data.monthRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LiveExpenseApp
    private val dao = app.database.expenseDao()

    val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val payments: StateFlow<List<Payment>> = month
        .flatMapLatest { paymentsIn(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val unparsed: StateFlow<List<UnparsedMessage>> = dao.unparsedMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private fun paymentsIn(month: YearMonth): Flow<List<Payment>> {
        val (from, until) = monthRange(month, ZoneId.systemDefault())
        return dao.paymentsBetween(from, until)
    }

    fun changeMonth(by: Long) {
        month.value = month.value.plusMonths(by)
    }

    fun categorize(paymentId: Long, category: String) {
        viewModelScope.launch { app.expenses.categorize(paymentId, category) }
    }

    fun addManual(amountPaise: Long, payee: String, category: String?) {
        viewModelScope.launch {
            app.expenses.addManual(amountPaise, payee, category, System.currentTimeMillis())
        }
    }

    fun deleteUnparsed(id: Long) {
        viewModelScope.launch { dao.deleteUnparsed(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

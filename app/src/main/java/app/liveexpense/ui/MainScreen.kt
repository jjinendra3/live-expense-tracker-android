package app.liveexpense.ui

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liveexpense.data.Categories
import app.liveexpense.data.MonthSummary
import app.liveexpense.data.Payment
import app.liveexpense.data.UnparsedMessage
import app.liveexpense.data.formatRupees
import app.liveexpense.data.summarize
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy")
private val PAID_AT_FORMAT = DateTimeFormatter.ofPattern("d MMM, h:mm a")

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    missingPermissions: List<String>,
    onGrantPermissions: () -> Unit,
    paymentToOpen: Long?,
    onPaymentOpened: () -> Unit,
) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val unparsed by viewModel.unparsed.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Payment?>(null) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(paymentToOpen) {
        if (paymentToOpen != null) viewModel.month.value = YearMonth.now()
    }

    LaunchedEffect(paymentToOpen, payments) {
        val target = payments.firstOrNull { it.id == paymentToOpen } ?: return@LaunchedEffect
        tab = 0
        editing = target
        onPaymentOpened()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Live Expense") }) },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(onClick = { adding = true }) { Text("Add cash") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (missingPermissions.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Needs SMS and notification access to catch payments. Nothing leaves this phone.")
                        Button(onClick = onGrantPermissions) { Text("Grant access") }
                    }
                }
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Payments") })
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(if (unparsed.isEmpty()) "Unparsed" else "Unparsed (${unparsed.size})") },
                )
            }
            when (tab) {
                0 -> PaymentsTab(
                    month = month,
                    payments = payments,
                    onChangeMonth = viewModel::changeMonth,
                    onPaymentClick = { editing = it },
                )
                else -> UnparsedTab(unparsed, onDelete = viewModel::deleteUnparsed)
            }
        }
    }

    editing?.let { payment ->
        CategoryDialog(
            payment = payment,
            onDismiss = { editing = null },
            onPick = { category ->
                viewModel.categorize(payment.id, category)
                editing = null
            },
        )
    }

    if (adding) {
        AddCashDialog(
            onDismiss = { adding = false },
            onAdd = { paise, payee, category ->
                viewModel.addManual(paise, payee, category)
                adding = false
            },
        )
    }
}

@Composable
private fun PaymentsTab(
    month: YearMonth,
    payments: List<Payment>,
    onChangeMonth: (Long) -> Unit,
    onPaymentClick: (Payment) -> Unit,
) {
    val summary = remember(payments) { summarize(payments) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { onChangeMonth(-1) }) { Text("Previous") }
                Text(month.format(MONTH_FORMAT), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onChangeMonth(1) }, enabled = month < YearMonth.now()) { Text("Next") }
            }
        }
        item { SummaryCard(summary) }
        if (payments.isEmpty()) {
            item {
                Text(
                    "No payments this month.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(payments, key = { it.id }) { payment ->
            PaymentRow(payment, onClick = { onPaymentClick(payment) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun SummaryCard(summary: MonthSummary) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Spent", style = MaterialTheme.typography.labelLarge)
            Text(formatRupees(summary.spentPaise), style = MaterialTheme.typography.headlineMedium)
            summary.byCategory.forEach { total ->
                AmountRow(total.category, total.paise)
            }
            if (summary.uncategorizedCount > 0) {
                AmountRow("Uncategorized (${summary.uncategorizedCount})", summary.uncategorizedPaise)
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, paise: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(formatRupees(paise), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PaymentRow(payment: Payment, onClick: () -> Unit) {
    val paidAt = remember(payment.paidAt) {
        Instant.ofEpochMilli(payment.paidAt).atZone(ZoneId.systemDefault()).format(PAID_AT_FORMAT)
    }
    val instrument = listOfNotNull(payment.instrument, payment.accountLast?.let { "••$it" }).joinToString(" ")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                payment.payee,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("$instrument · $paidAt", style = MaterialTheme.typography.bodySmall)
            Text(
                payment.category ?: "Uncategorized",
                style = MaterialTheme.typography.bodySmall,
                color = if (payment.category == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(formatRupees(payment.amountPaise), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CategoryDialog(payment: Payment, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${formatRupees(payment.amountPaise)} · ${payment.payee}") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (Categories.all + Categories.NOT_AN_EXPENSE).forEach { category ->
                    FilterChip(
                        selected = category == payment.category,
                        onClick = { onPick(category) },
                        label = { Text(category) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun AddCashDialog(onDismiss: () -> Unit, onAdd: (Long, String, String?) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var payee by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val paise = remember(amount) { toPaiseOrNull(amount) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add cash payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = payee,
                    onValueChange = { payee = it },
                    label = { Text("Paid to") },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Categories.all.forEach { option ->
                        FilterChip(
                            selected = option == category,
                            onClick = { category = option },
                            label = { Text(option) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = paise != null && payee.isNotBlank(),
                onClick = { paise?.let { onAdd(it, payee.trim(), category) } },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun toPaiseOrNull(amount: String): Long? =
    amount.trim().toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO && it.scale() <= 2 }
        ?.movePointRight(2)
        ?.toLong()

@Composable
private fun UnparsedTab(messages: List<UnparsedMessage>, onDelete: (Long) -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            item {
                Text(
                    "Bank messages the app couldn't read show up here. None so far.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(messages, key = { it.id }) { message ->
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(message.sender, style = MaterialTheme.typography.labelLarge)
                Text(message.body, style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Bank message", message.body)))
                            }
                        },
                    ) { Text("Copy") }
                    TextButton(onClick = { onDelete(message.id) }) { Text("Delete") }
                }
            }
            HorizontalDivider()
        }
    }
}

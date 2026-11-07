package app.liveexpense.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.liveexpense.prompt.PaymentPrompt

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val paymentToOpen = mutableStateOf<Long?>(null)
    private val missingPermissions = mutableStateOf(emptyList<String>())

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissions()
        openPaymentFrom(intent)
        setContent {
            AppTheme {
                MainScreen(
                    viewModel = viewModel,
                    missingPermissions = missingPermissions.value,
                    onGrantPermissions = { permissionRequest.launch(missingPermissions.value.toTypedArray()) },
                    paymentToOpen = paymentToOpen.value,
                    onPaymentOpened = { paymentToOpen.value = null },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPaymentFrom(intent)
    }

    private fun openPaymentFrom(intent: Intent) {
        val id = intent.getLongExtra(EXTRA_PAYMENT_ID, -1)
        if (id == -1L) return
        paymentToOpen.value = id
        // Action buttons don't dismiss a notification on their own.
        NotificationManagerCompat.from(this).cancel(PaymentPrompt.NOTIFICATION_ID)
    }

    private fun refreshPermissions() {
        missingPermissions.value = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        const val EXTRA_PAYMENT_ID = "payment_id"
        private val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}

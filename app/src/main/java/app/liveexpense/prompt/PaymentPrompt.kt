package app.liveexpense.prompt

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import app.liveexpense.R
import app.liveexpense.data.Categories
import app.liveexpense.data.Payment
import app.liveexpense.data.PendingPrompt
import app.liveexpense.data.formatRupees
import app.liveexpense.ui.MainActivity
import java.util.concurrent.TimeUnit

object PaymentPrompt {
    // One id, so a new prompt replaces the last. Android bundles several open notifications from an app
    // and marks new ones silent, which would stop the banner. Replaced payments stay saved as uncategorized.
    const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "payment_prompts"
    private const val CONFIRMATION_CHANNEL_ID = "payment_confirmations"
    private val TIMEOUT_MS = TimeUnit.HOURS.toMillis(3)
    private val CONFIRMATION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(6)
    private val CHIP_IDS = listOf(
        R.id.prompt_chip_0,
        R.id.prompt_chip_1,
        R.id.prompt_chip_2,
        R.id.prompt_chip_3,
    )

    fun createChannels(context: Context) {
        val prompts = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.prompt_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        // High importance keeps it on screen. No sound, since the prompt it replaces already made one.
        val confirmations = NotificationChannel(
            CONFIRMATION_CHANNEL_ID,
            context.getString(R.string.confirmation_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { setSound(null, null) }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(prompts, confirmations))
    }

    /** Replaces the prompt, so the user sees which choice landed and can fix a mistaken tap. */
    fun showConfirmation(context: Context, payment: Payment) {
        val notification = NotificationCompat.Builder(context, CONFIRMATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("✓ ${payment.category}")
            .setContentText("${formatRupees(payment.amountPaise)} · ${payment.payee}")
            .setContentIntent(openAppIntent(context, payment.id))
            .addAction(0, context.getString(R.string.prompt_change), openAppIntent(context, payment.id))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(CONFIRMATION_TIMEOUT_MS)
            .build()

        // Updating the prompt in place left the banner up for only 1.5s on the S23. A new notification gets longer.
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        post(context, notification)
    }

    fun show(context: Context, prompt: PendingPrompt) {
        val payment = prompt.payment
        val content = RemoteViews(context.packageName, R.layout.prompt_content).apply {
            setTextViewText(R.id.prompt_title, "${formatRupees(payment.amountPaise)} · ${payment.payee}")
            setTextViewText(
                R.id.prompt_instrument,
                listOfNotNull(payment.instrument, payment.accountLast?.let { "••$it" }).joinToString(" "),
            )
            CHIP_IDS.zip(prompt.categories).forEach { (chipId, category) ->
                setTextViewText(chipId, category)
                setOnClickPendingIntent(chipId, categoryIntent(context, payment.id, category))
                if (category == prompt.suggested) {
                    setInt(chipId, "setBackgroundResource", R.drawable.prompt_chip_suggested)
                }
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(content)
            .setCustomHeadsUpContentView(content)
            .setCustomBigContentView(content)
            .setContentIntent(openAppIntent(context, payment.id))
            .addAction(
                0,
                context.getString(R.string.prompt_not_an_expense),
                categoryIntent(context, payment.id, Categories.NOT_AN_EXPENSE),
            )
            .addAction(0, context.getString(R.string.prompt_other), openAppIntent(context, payment.id))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()

        post(context, notification)
    }

    private fun post(context: Context, notification: Notification) {
        val granted = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        // The payment is already saved, so a blocked notification loses nothing.
        if (!granted) return
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun categoryIntent(context: Context, paymentId: Long, category: String): PendingIntent {
        val intent = Intent(context, PromptActionReceiver::class.java)
            // Distinct data keeps each payment and category from sharing one PendingIntent.
            .setData("liveexpense://prompt/$paymentId/${category.hashCode()}".toUri())
            .putExtra(PromptActionReceiver.EXTRA_PAYMENT_ID, paymentId)
            .putExtra(PromptActionReceiver.EXTRA_CATEGORY, category)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openAppIntent(context: Context, paymentId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData("liveexpense://open/$paymentId".toUri())
            .putExtra(MainActivity.EXTRA_PAYMENT_ID, paymentId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

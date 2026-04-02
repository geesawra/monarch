package industries.geesawra.monarch.datalayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.URL
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import industries.geesawra.monarch.MainActivity
import industries.geesawra.monarch.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.graphics.createBitmap

class MessagingService : FirebaseMessagingService() {

    private fun downloadBitmap(url: String): Bitmap? {
        return runCatching {
            URL(url).openStream().use { BitmapFactory.decodeStream(it) }
        }.onFailure {
            Log.e(TAG, "Failed to download notification image: ${it.message}")
        }.getOrNull()
    }

    private fun toCircularBitmap(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val output = createBitmap(size, size)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint().apply { isAntiAlias = true }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        val left = (size - source.width) / 2f
        val top = (size - source.height) / 2f
        canvas.drawBitmap(source, left, top, paint)
        return output
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MessagingServiceEntryPoint {
        fun pushNotificationManager(): PushNotificationManager
        fun accountManager(): AccountManager
    }

    companion object {
        private const val TAG = "MessagingService"
        private const val GROUP_KEY = "monarch_notification_group"
        private const val SUMMARY_ID = 0
        const val CHANNEL_ID = "monarch_notifications"
        const val CHANNEL_NAME = "Notifications"

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Bluesky notifications"
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getEntryPoint(): MessagingServiceEntryPoint {
        return EntryPointAccessors.fromApplication(
            applicationContext,
            MessagingServiceEntryPoint::class.java
        )
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token received")
        val entryPoint = getEntryPoint()
        val pushManager = entryPoint.pushNotificationManager()
        val accountManager = entryPoint.accountManager()

        CoroutineScope(Dispatchers.IO).launch {
            pushManager.saveTokenLocally(token)
            val did = accountManager.getActiveDid()
            if (did != null) {
                pushManager.registerToken(token, did)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Message received: ${message.data}")
        try { onMessageReceivedInner(message) } catch (e: Exception) {
            Log.e(TAG, "Failed to handle notification", e)
        }
    }

    private fun onMessageReceivedInner(message: RemoteMessage) {

        val title = message.notification?.title ?: message.data["title"] ?: "Monarch"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val imageUrl = message.notification?.imageUrl?.toString() ?: message.data["image"]
        val embedImageUrl = message.data["embedImage"]?.ifEmpty { null }
        val quotedText = message.data["quotedText"]?.ifEmpty { null }
        val quotedEmbedImage = message.data["quotedEmbedImage"]?.ifEmpty { null }
        val kind = message.data["kind"]
        val uri = message.data["uri"]
        val authorDid = message.data["authorDid"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            kind?.let { putExtra("notification_kind", it) }
            uri?.let { putExtra("notification_uri", it) }
            authorDid?.let { putExtra("notification_author_did", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val image = imageUrl?.let { downloadBitmap(it) }
        val avatar = image?.let { toCircularBitmap(it) }

        val embedBitmap = embedImageUrl?.let { downloadBitmap(it) }
        val quotedImageBitmap = quotedEmbedImage?.let { downloadBitmap(it) }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (avatar != null) {
            builder.setLargeIcon(avatar)
        }

        when {
            embedBitmap != null -> {
                val style = NotificationCompat.BigPictureStyle()
                    .bigPicture(embedBitmap)
                if (quotedText != null) {
                    style.setSummaryText(quotedText)
                }
                builder.setStyle(style)
            }
            quotedImageBitmap != null -> {
                val style = NotificationCompat.BigPictureStyle()
                    .bigPicture(quotedImageBitmap)
                if (quotedText != null) {
                    style.setSummaryText(quotedText)
                }
                builder.setStyle(style)
            }
            quotedText != null -> {
                val fullText = if (body.isNotEmpty()) "$body\n\n$quotedText" else quotedText
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            }
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())

        val summaryBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        notificationManager.notify(SUMMARY_ID, summaryBuilder.build())
    }
}

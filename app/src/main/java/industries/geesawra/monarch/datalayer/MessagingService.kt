package industries.geesawra.monarch.datalayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
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
        val body = message.notification?.body ?: message.data["body"] ?: return
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

        val expandedView = RemoteViews(packageName, R.layout.notification_expanded).apply {
            setTextViewText(R.id.notification_title, title)
            when (kind) {
                "app.bsky.graph.follow" -> {
                    setViewVisibility(R.id.notification_body, View.GONE)
                }
                else -> {
                    setTextViewText(R.id.notification_body, body)
                }
            }
        }

        if (quotedText != null || quotedEmbedImage != null) {
            expandedView.setViewVisibility(R.id.notification_quote_container, View.VISIBLE)
            if (quotedText != null) {
                expandedView.setTextViewText(R.id.notification_quoted_text, quotedText)
            }
            if (quotedEmbedImage != null) {
                val quotedBitmap = downloadBitmap(quotedEmbedImage)
                if (quotedBitmap != null) {
                    expandedView.setImageViewBitmap(R.id.notification_quoted_image, quotedBitmap)
                    expandedView.setViewVisibility(R.id.notification_quoted_image, View.VISIBLE)
                }
            }
        }

        if (embedImageUrl != null) {
            val embedBitmap = downloadBitmap(embedImageUrl)
            if (embedBitmap != null) {
                expandedView.setImageViewBitmap(R.id.notification_image, embedBitmap)
                expandedView.setViewVisibility(R.id.notification_image, View.VISIBLE)
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(expandedView)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())

        val summaryBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        notificationManager.notify(SUMMARY_ID, summaryBuilder.build())
    }
}

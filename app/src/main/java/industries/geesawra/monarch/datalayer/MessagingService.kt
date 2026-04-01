package industries.geesawra.monarch.datalayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat
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

    private fun notificationIdForPerson(key: String): Int = key.hashCode()

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Message received: ${message.data}")

        val title = message.notification?.title ?: message.data["title"] ?: "Monarch"
        val body = message.notification?.body ?: message.data["body"] ?: return
        val imageUrl = message.notification?.imageUrl?.toString() ?: message.data["image"]
        val embedImageUrl = message.data["embedImage"]
        val senderKey = message.data["authorDid"] ?: message.data["authorHandle"] ?: title

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val image = imageUrl?.let { downloadBitmap(it) }

        val boldTitle = SpannableString(title).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, 0)
        }

        val personBuilder = Person.Builder()
            .setName(boldTitle)
            .setKey(senderKey)
        if (image != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(toCircularBitmap(image)))
        }
        val person = personBuilder.build()

        val notificationId = notificationIdForPerson(senderKey)
        val notificationManager = getSystemService(NotificationManager::class.java)

        val existingStyle = notificationManager.activeNotifications
            .find { it.id == notificationId }
            ?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }

        val msg = NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), person)
        if (embedImageUrl != null) {
            val embedBitmap = downloadBitmap(embedImageUrl)
            if (embedBitmap != null) {
                val imageDir = File(cacheDir, "notification_images").apply { mkdirs() }
                val imageFile = File(imageDir, "${System.currentTimeMillis()}.jpg")
                imageFile.outputStream().use { embedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                val contentUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", imageFile)
                msg.setData("image/jpeg", contentUri)
            }
        }

        val style = (existingStyle ?: NotificationCompat.MessagingStyle(person))
            .addMessage(msg)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(style)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notificationId, builder.build())

        val summaryBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        notificationManager.notify(SUMMARY_ID, summaryBuilder.build())
    }
}

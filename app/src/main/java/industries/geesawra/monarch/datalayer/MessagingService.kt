package industries.geesawra.monarch.datalayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
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

class MessagingService : FirebaseMessagingService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MessagingServiceEntryPoint {
        fun pushNotificationManager(): PushNotificationManager
        fun accountManager(): AccountManager
    }

    companion object {
        private const val TAG = "MessagingService"
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

        val title = message.notification?.title ?: message.data["title"] ?: "Monarch"
        val body = message.notification?.body ?: message.data["body"] ?: return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(message.messageId?.hashCode() ?: 0, notification)
    }
}

package industries.geesawra.monarch.datalayer

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sh.christian.ozone.api.AtUri

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_LIKE = "industries.geesawra.monarch.ACTION_LIKE"
        const val ACTION_REPLY = "industries.geesawra.monarch.ACTION_REPLY"
        const val EXTRA_POST_URI = "post_uri"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val KEY_REPLY_TEXT = "reply_text"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val postUri = intent.getStringExtra(EXTRA_POST_URI) ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = BlueskyConn(context)
                val posts = conn.getPosts(listOf(AtUri(postUri))).getOrNull()
                val post = posts?.firstOrNull() ?: return@launch

                when (intent.action) {
                    ACTION_LIKE -> {
                        conn.like(post.uri, post.cid)
                    }
                    ACTION_REPLY -> {
                        val remoteInput = RemoteInput.getResultsFromIntent(intent)
                        val replyText = remoteInput?.getCharSequence(KEY_REPLY_TEXT)?.toString()
                        if (replyText.isNullOrBlank()) return@launch

                        val skeetData = SkeetData.fromPostView(post, post.author)
                        val replyRef = skeetData.replyRef()

                        conn.post(
                            content = replyText,
                            replyRef = replyRef,
                        )

                        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(notificationId)

                        val prefs = context.settingsDataStore.data.first()
                        val autoLike = prefs[stringPreferencesKey("auto_like_on_reply")]?.toBooleanStrictOrNull() ?: false
                        if (autoLike && post.viewer?.like == null) {
                            conn.like(post.uri, post.cid)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

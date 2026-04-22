package industries.geesawra.monarch.datalayer

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Handle
import sh.christian.ozone.oauth.OAuthToken

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_LIKE = "industries.geesawra.monarch.ACTION_LIKE"
        const val ACTION_REPLY = "industries.geesawra.monarch.ACTION_REPLY"
        const val EXTRA_POST_URI = "post_uri"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_RECIPIENT_DID = "recipient_did"
        const val KEY_REPLY_TEXT = "reply_text"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun accountManager(): AccountManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val postUri = intent.getStringExtra(EXTRA_POST_URI) ?: return
        val recipientDid = intent.getStringExtra(EXTRA_RECIPIENT_DID)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = BlueskyConn(context)

                if (recipientDid != null) {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        ReceiverEntryPoint::class.java
                    )
                    val account = entryPoint.accountManager().getAccount(recipientDid)
                    if (account != null) {
                        val token = BlueskyJson.decodeFromString(
                            OAuthToken.serializer(),
                            account.oauthTokenJson,
                        )
                        conn.initializeInMemory(
                            pdsURL = account.pdsHost,
                            appviewProxy = account.appviewProxy,
                            oauthToken = token,
                            handle = Handle(account.handle),
                            authServerURL = account.authServerURL,
                        )
                    }
                }

                val posts = conn.getPosts(listOf(AtUri(postUri))).getOrNull()
                val post = posts?.firstOrNull() ?: return@launch

                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                when (intent.action) {
                    ACTION_LIKE -> {
                        val result = conn.like(post.uri, post.cid)
                        if (result.isSuccess) {
                            nm.cancel(notificationId)
                        } else {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(
                                    context,
                                    "Failed to like post",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
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

                        nm.cancel(notificationId)

                        val prefs = context.settingsDataStore.data.first()
                        val autoLike = prefs[stringPreferencesKey("auto_like_on_reply")]?.toBooleanStrictOrNull() ?: false
                        if (autoLike && post.viewer?.like == null) {
                            conn.like(post.uri, post.cid)
                        }
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // User cancelled the action (e.g. dismissed notification); ignore silently.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

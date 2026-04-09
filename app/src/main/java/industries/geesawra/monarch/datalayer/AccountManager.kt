package industries.geesawra.monarch.datalayer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val Context.accountsDataStore by preferencesDataStore("accounts")
internal val ACCOUNTS_LIST_KEY = stringPreferencesKey("accounts_list")
internal val ACTIVE_DID_KEY = stringPreferencesKey("active_did")

@Serializable
data class StoredAccount(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val pdsHost: String,
    val appviewProxy: String,
    val oauthTokenJson: String,
)

/**
 * Read the currently-active stored account directly from the DataStore.
 *
 * Used by `BlueskyConn` from outside the Hilt graph (it isn't injected). Schema knowledge stays in
 * this file; callers don't touch the keys directly. Returns null if there's no active account or
 * if the persisted JSON has drifted (old `sessionJson`-based schema gets nuked the same way).
 */
internal suspend fun Context.readActiveStoredAccount(): StoredAccount? {
    val prefs = accountsDataStore.data.first()
    val activeDid = prefs[ACTIVE_DID_KEY] ?: return null
    val accountsJson = prefs[ACCOUNTS_LIST_KEY] ?: return null
    val accounts = runCatching {
        Json.decodeFromString<List<StoredAccount>>(accountsJson)
    }.getOrNull() ?: return null
    return accounts.firstOrNull { it.did == activeDid }
}

/**
 * Update the persisted OAuth token JSON for one account in place. Used by `BlueskyConn`'s
 * token-refresh watcher to keep the on-disk credentials in sync with the SDK's refreshed
 * in-memory tokens, so a process restart picks up the latest state.
 */
internal suspend fun Context.updateStoredAccountOAuthToken(did: String, oauthTokenJson: String) {
    val prefs = accountsDataStore.data.first()
    val accountsJson = prefs[ACCOUNTS_LIST_KEY] ?: return
    val accounts = runCatching {
        Json.decodeFromString<List<StoredAccount>>(accountsJson)
    }.getOrNull() ?: return
    val updated = accounts.map {
        if (it.did == did) it.copy(oauthTokenJson = oauthTokenJson) else it
    }
    accountsDataStore.edit {
        it[ACCOUNTS_LIST_KEY] = Json.encodeToString(updated)
    }
}

@Singleton
class AccountManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getAccounts(): List<StoredAccount> {
        val json = context.accountsDataStore.data.map { it[ACCOUNTS_LIST_KEY] ?: "[]" }.first()
        return runCatching { Json.decodeFromString<List<StoredAccount>>(json) }.getOrDefault(emptyList())
    }

    suspend fun getActiveDid(): String? {
        return context.accountsDataStore.data.map { it[ACTIVE_DID_KEY] }.first()
    }

    suspend fun addAccount(account: StoredAccount) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.did == account.did }
        accounts.add(account)
        context.accountsDataStore.edit {
            it[ACCOUNTS_LIST_KEY] = Json.encodeToString(accounts)
            it[ACTIVE_DID_KEY] = account.did
        }
    }

    suspend fun removeAccount(did: String) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.did == did }
        context.accountsDataStore.edit {
            it[ACCOUNTS_LIST_KEY] = Json.encodeToString(accounts)
            if (it[ACTIVE_DID_KEY] == did) {
                val next = accounts.firstOrNull()
                if (next != null) {
                    it[ACTIVE_DID_KEY] = next.did
                } else {
                    it.remove(ACTIVE_DID_KEY)
                }
            }
        }
    }

    suspend fun updateAccountOAuthToken(did: String, oauthTokenJson: String) {
        val accounts = getAccounts().toMutableList()
        val idx = accounts.indexOfFirst { it.did == did }
        if (idx >= 0) {
            accounts[idx] = accounts[idx].copy(oauthTokenJson = oauthTokenJson)
            context.accountsDataStore.edit {
                it[ACCOUNTS_LIST_KEY] = Json.encodeToString(accounts)
            }
        }
    }

    suspend fun updateAccountAppviewProxy(did: String, appviewProxy: String) {
        val accounts = getAccounts().toMutableList()
        val idx = accounts.indexOfFirst { it.did == did }
        if (idx >= 0) {
            accounts[idx] = accounts[idx].copy(appviewProxy = appviewProxy)
            context.accountsDataStore.edit {
                it[ACCOUNTS_LIST_KEY] = Json.encodeToString(accounts)
            }
        }
    }

    suspend fun updateAccountProfile(did: String, displayName: String?, avatarUrl: String?) {
        val accounts = getAccounts().toMutableList()
        val idx = accounts.indexOfFirst { it.did == did }
        if (idx >= 0) {
            accounts[idx] = accounts[idx].copy(displayName = displayName, avatarUrl = avatarUrl)
            context.accountsDataStore.edit {
                it[ACCOUNTS_LIST_KEY] = Json.encodeToString(accounts)
            }
        }
    }

    suspend fun setActiveDid(did: String) {
        context.accountsDataStore.edit {
            it[ACTIVE_DID_KEY] = did
        }
    }

    suspend fun getAccount(did: String): StoredAccount? {
        return getAccounts().firstOrNull { it.did == did }
    }

    suspend fun hasAccounts(): Boolean {
        return getAccounts().isNotEmpty()
    }
}

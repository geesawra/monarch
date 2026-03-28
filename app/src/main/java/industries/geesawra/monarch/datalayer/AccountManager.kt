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

private val Context.accountsDataStore by preferencesDataStore("accounts")

@Serializable
data class StoredAccount(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val pdsHost: String,
    val appviewProxy: String,
    val sessionJson: String,
)

@Singleton
class AccountManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val ACCOUNTS_LIST = stringPreferencesKey("accounts_list")
        private val ACTIVE_DID = stringPreferencesKey("active_did")
    }

    suspend fun getAccounts(): List<StoredAccount> {
        val json = context.accountsDataStore.data.map { it[ACCOUNTS_LIST] ?: "[]" }.first()
        return runCatching { Json.decodeFromString<List<StoredAccount>>(json) }.getOrDefault(emptyList())
    }

    suspend fun getActiveDid(): String? {
        return context.accountsDataStore.data.map { it[ACTIVE_DID] }.first()
    }

    suspend fun addAccount(account: StoredAccount) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.did == account.did }
        accounts.add(account)
        context.accountsDataStore.edit {
            it[ACCOUNTS_LIST] = Json.encodeToString(accounts)
            it[ACTIVE_DID] = account.did
        }
    }

    suspend fun removeAccount(did: String) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.did == did }
        context.accountsDataStore.edit {
            it[ACCOUNTS_LIST] = Json.encodeToString(accounts)
            if (it[ACTIVE_DID] == did) {
                val next = accounts.firstOrNull()
                if (next != null) {
                    it[ACTIVE_DID] = next.did
                } else {
                    it.remove(ACTIVE_DID)
                }
            }
        }
    }

    suspend fun updateAccountSession(did: String, sessionJson: String) {
        val accounts = getAccounts().toMutableList()
        val idx = accounts.indexOfFirst { it.did == did }
        if (idx >= 0) {
            accounts[idx] = accounts[idx].copy(sessionJson = sessionJson)
            context.accountsDataStore.edit {
                it[ACCOUNTS_LIST] = Json.encodeToString(accounts)
            }
        }
    }

    suspend fun updateAccountProfile(did: String, displayName: String?, avatarUrl: String?) {
        val accounts = getAccounts().toMutableList()
        val idx = accounts.indexOfFirst { it.did == did }
        if (idx >= 0) {
            accounts[idx] = accounts[idx].copy(displayName = displayName, avatarUrl = avatarUrl)
            context.accountsDataStore.edit {
                it[ACCOUNTS_LIST] = Json.encodeToString(accounts)
            }
        }
    }

    suspend fun setActiveDid(did: String) {
        context.accountsDataStore.edit {
            it[ACTIVE_DID] = did
        }
    }

    suspend fun getAccount(did: String): StoredAccount? {
        return getAccounts().firstOrNull { it.did == did }
    }

    suspend fun hasAccounts(): Boolean {
        return getAccounts().isNotEmpty()
    }
}

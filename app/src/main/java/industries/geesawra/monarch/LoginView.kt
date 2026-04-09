package industries.geesawra.monarch

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import industries.geesawra.monarch.datalayer.BlueskyConn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.christian.ozone.api.Handle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginView(
    modifier: Modifier = Modifier,
    blueskyConn: BlueskyConn? = null,
    navigate: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var isPasswordFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val bc = blueskyConn ?: BlueskyConn(ctx)
    val appPasswordRegex = "[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}".toRegex()
    var currentPDS by remember { mutableStateOf("") }
    var lookingUpPDS by remember { mutableStateOf(false) }
    val handleTextFieldError = remember { mutableStateOf(false) }
    val loggingIn = remember { mutableStateOf(false) }
    var handle by remember { mutableStateOf("") }
    handle.useDebounce { it, scope ->
        val handle = it
        if (handle.isEmpty()) {
            return@useDebounce
        }

        if (!handle.isATHandle() && !handle.isDID()) {
            return@useDebounce
        }

        scope.launch {
            lookingUpPDS = true
            currentPDS = BlueskyConn.pdsForHandle(handle).onSuccess {
                handleTextFieldError.value = false
                Result.success("")
            }.onFailure {
                Toast.makeText(
                    ctx,
                    it.message ?: "Can't look up PDS: ${it.toString()}",
                    Toast.LENGTH_LONG
                ).show()
                handleTextFieldError.value = true
                it
            }.getOrDefault("")
            lookingUpPDS = false
        }

    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp)
            .imePadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Log into your Bluesky account",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        TextField(
            value = handle,
            isError = handleTextFieldError.value,
            onValueChange = { handle = it },
            label = { Text("Handle (e.g., yourname.bsky.social)") },
            modifier = Modifier
                .fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
            )
        )
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.None
            ),
            trailingIcon = {
                val image = if (passwordVisible)
                    Icons.Filled.Visibility
                else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, description)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .onFocusChanged { focusState -> isPasswordFocused = focusState.isFocused }
        )


        val proxies = listOf(
            "Bluesky Appview" to "did:web:api.bsky.app#bsky_appview",
            "Blacksky Appview" to "did:web:api.blacksky.community#bsky_appview"
        )

        val expanded = remember { mutableStateOf(false) }
        val selectedProxyPretty = remember { mutableStateOf(proxies.first().first) }
        val selectedProxy = remember { mutableStateOf(proxies.first().second) }


        ExposedDropdownMenuBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            expanded = expanded.value,
            onExpandedChange = {
                expanded.value = !expanded.value
            }
        ) {
            TextField(
                value = selectedProxyPretty.value,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                modifier = Modifier
                    .fillMaxWidth(),
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false }
            ) {
                proxies.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(text = item.first) },
                        onClick = {
                            selectedProxyPretty.value = item.first
                            selectedProxy.value = item.second
                            expanded.value = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    loggingIn.value = true
                    bc.login(currentPDS, handle, password, selectedProxy.value).onSuccess {
                        navigate()
                    }.onFailure {
                        Log.e("LoginView", "Login failed", it)
                        Toast.makeText(ctx, it.message ?: "Unknown login error", Toast.LENGTH_LONG)
                            .show()
                    }
                    loggingIn.value = false
                }
            },
            enabled = (handle.isATHandle() || handle.isDID()) && password.isNotEmpty() && currentPDS.isNotEmpty() && !loggingIn.value,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        ) {
            Text("Login")
        }

        FilledTonalButton(
            onClick = {
                scope.launch {
                    loggingIn.value = true
                    bc.oauthBeginLogin(handle, selectedProxy.value).onSuccess { authUrl ->
                        val intent = CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
                        intent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        intent.launchUrl(ctx, Uri.parse(authUrl))
                    }.onFailure {
                        Log.e("LoginView", "OAuth begin failed", it)
                        Toast.makeText(
                            ctx,
                            it.message ?: "Failed to start OAuth login",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    loggingIn.value = false
                }
            },
            enabled = (handle.isATHandle() || handle.isDID()) && currentPDS.isNotEmpty() && !loggingIn.value,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text("Sign in with Bluesky (OAuth)")
        }

        var pdsString = "I'll look up your PDS automatically :^)"
        if (currentPDS != "") {
            pdsString = if (currentPDS.endsWith("bsky.network")) {
                "Your PDS: bsky.app"
            } else {
                "Your PDS: $currentPDS"
            }
        }
        if (lookingUpPDS) {
            pdsString = "Looking up your PDS..."
        }

        Text(
            text = pdsString,
            style = MaterialTheme.typography.labelMedium
        )

        if (password.isNotEmpty() && !password.matches(appPasswordRegex) && !isPasswordFocused) {
            Text(
                text = "Hint: Consider using an app password (e.g., xxxx-xxxx-xxxx-xxxx).",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

fun String.isATHandle(): Boolean {
    return Handle.Regex.matches(this.removePrefix("@"))
}

fun String.isDID(): Boolean = startsWith("did:")

@Composable
fun <T> T.useDebounce(
    delayMillis: Long = 300L,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    onChange: (T, CoroutineScope) -> Unit
): T {
    val state by rememberUpdatedState(this)

    DisposableEffect(state) {
        val job = coroutineScope.launch {
            delay(delayMillis)
            onChange(state, coroutineScope)
        }
        onDispose {
            job.cancel()
        }
    }
    return state
}

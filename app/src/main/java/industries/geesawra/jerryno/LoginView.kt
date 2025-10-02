package industries.geesawra.jerryno

import android.util.Log
import android.widget.Toast
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
import industries.geesawra.jerryno.datalayer.BlueskyConn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.christian.ozone.api.Handle

@Composable
fun LoginView(
    modifier: Modifier = Modifier,
    navigate: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var isPasswordFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val bc = BlueskyConn(ctx)
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

        if (!handle.isATHandle()) {
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
            style = MaterialTheme.typography.titleLarge,
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

        Button(
            onClick = {
                scope.launch {
                    loggingIn.value = true
                    bc.login(currentPDS, handle, password).onSuccess {
                        navigate()
                    }.onFailure {
                        Log.e("LoginView", "Login failed", it)
                        Toast.makeText(ctx, it.message ?: "Unknown login error", Toast.LENGTH_LONG)
                            .show()
                    }
                    loggingIn.value = false
                }
            },
            enabled = handle.isATHandle() && password.isNotEmpty() && currentPDS.isNotEmpty() && !loggingIn.value,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        ) {
            Text("Login")
        }

        var pdsString = "I'll look up your PDS automatically :^)"
        if (currentPDS != "") {
            if (currentPDS.endsWith("bsky.network")) {
                pdsString = "Your PDS: bsky.app"
            } else {
                pdsString = "Your PDS: $currentPDS"
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
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

fun String.isATHandle(): Boolean {
    return Handle.Regex.matches(this.removePrefix("@"))
}

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

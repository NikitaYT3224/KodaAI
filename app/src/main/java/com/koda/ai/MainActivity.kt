package com.koda.ai

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val chatVm: ChatViewModel by viewModels()
    private val authVm: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colors) {
                val auth by authVm.state.collectAsStateWithLifecycle()
                val apiKey = auth.apiKey

                if (apiKey == null) {
                    LoginScreen(state = auth, onLogin = authVm::login)
                } else {
                    ChatScreen(vm = chatVm, apiKey = apiKey, onLogout = authVm::logout)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, apiKey: String, onLogout: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            vm.clearError()
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KodaAI") },
                actions = {
                    TextButton(onClick = onLogout) { Text("Выйти") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages) { message ->
                    MessageBubble(message)
                }
                if (state.loading) {
                    item {
                        Text(
                            text = "KodaAI печатает…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Напишите сообщение") },
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        vm.send(input, apiKey)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !state.loading
                ) {
                    Text("→")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (message.fromUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = message.text.ifEmpty { "…" },
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

package com.koda.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(state: AuthState, onLogin: (String) -> Unit) {
    var keyInput by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("KodaAI", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Введите API-ключ, чтобы продолжить",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text("API-ключ") },
            singleLine = true,
            enabled = !state.checking,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (keyInput.isNotBlank()) onLogin(keyInput)
            }),
            trailingIcon = {
                TextButton(onClick = { visible = !visible }) {
                    Text(if (visible) "Скрыть" else "Показать")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onLogin(keyInput) },
            enabled = keyInput.isNotBlank() && !state.checking,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Войти")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Ключ хранится только на этом устройстве, в зашифрованном виде.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

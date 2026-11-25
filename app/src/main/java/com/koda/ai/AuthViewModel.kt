package com.koda.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthState(
    val apiKey: String? = null,   // null — ключа нет, нужен вход
    val checking: Boolean = false,
    val error: String? = null
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = KeyStorage(app)
    private val _state = MutableStateFlow(AuthState(apiKey = storage.load()))
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun login(rawKey: String) {
        val key = rawKey.trim()
        if (key.isEmpty() || _state.value.checking) return

        _state.update { it.copy(checking = true, error = null) }

        viewModelScope.launch {
            try {
                if (KodaApi.verifyKey(key)) {
                    storage.save(key)
                    _state.update { AuthState(apiKey = key) }
                } else {
                    _state.update {
                        it.copy(checking = false, error = "Сервер не принял этот ключ")
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(checking = false, error = "Нет связи с сервером: ${e.message}")
                }
            }
        }
    }

    fun logout() {
        storage.clear()
        _state.update { AuthState() }
    }
}

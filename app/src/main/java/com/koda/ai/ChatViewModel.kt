package com.koda.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage("Привет! Я KodaAI. Чем могу помочь?", fromUser = false)
    ),
    val loading: Boolean = false,
    val error: String? = null
)

class ChatViewModel : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun send(text: String, apiKey: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.loading) return

        val history = _state.value.messages
        // сообщение пользователя окажется по индексу history.size, ответ — следующим
        val replyIndex = history.size + 1

        _state.update {
            it.copy(
                messages = it.messages +
                        ChatMessage(message, fromUser = true) +
                        ChatMessage("", fromUser = false),
                loading = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                KodaApi.stream(apiKey, history, message) { delta ->
                    _state.update { s ->
                        s.copy(messages = s.messages.mapIndexed { i, m ->
                            if (i == replyIndex) m.copy(text = m.text + delta) else m
                        })
                    }
                }
                _state.update { it.copy(loading = false) }
            } catch (e: Exception) {
                _state.update { s ->
                    s.copy(
                        messages = s.messages.filterIndexed { i, m ->
                            !(i == replyIndex && m.text.isEmpty())
                        },
                        loading = false,
                        error = e.message ?: "Ошибка сети"
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.service.AIProviderManager
import com.example.service.NetworkMonitor
import com.example.service.SettingsManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class AutomotiveChatViewModel(
    application: Application,
    private val aiProviderManager: AIProviderManager
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("automotive_chat_prefs", Context.MODE_PRIVATE)

    private val _messages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            content = "Hello! I'm Mekanik AI, your Automotive Assistant. Ask me anything about vehicle diagnostics, repairs, maintenance, or automotive systems.",
            isUser = false
        )
    ))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isChatOpen = MutableStateFlow(false)
    val isChatOpen: StateFlow<Boolean> = _isChatOpen.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    var posX by mutableStateOf(prefs.getFloat("chat_icon_x", 0.8f))
    var posY by mutableStateOf(prefs.getFloat("chat_icon_y", 0.7f))

    fun updatePosition(x: Float, y: Float) {
        posX = x
        posY = y
        prefs.edit {
            putFloat("chat_icon_x", x)
            putFloat("chat_icon_y", y)
        }
    }

    fun toggleChat() {
        _isChatOpen.value = !_isChatOpen.value
    }

    fun closeChat() {
        _isChatOpen.value = false
    }

    private val systemPrompt = """
        You are Mekanik AI, a highly specialized Automotive Assistant. 
        Your expertise is STRICTLY LIMITED to automotive-related topics including:
        - Vehicle diagnostics and OBD-II codes.
        - Preventive maintenance and service intervals.
        - Repair procedures and troubleshooting guides.
        - Engine, transmission, suspension, brake, and electrical systems.
        - Automotive parts, components, and vehicle specifications.
        - Workshop best practices and safety recommendations.

        RESTRICTIONS:
        - If the user asks about anything NOT related to the automotive domain (e.g., cooking, politics, general knowledge, sports, other technologies), you MUST politely respond: 
          "I am specialized exclusively for automotive topics and cannot provide answers outside the automotive domain. How can I help you with your vehicle today?"
        - Do not engage in casual conversation that isn't automotive-focused.
        - Keep your tone professional, authoritative, and helpful.
        - Use technical terms where appropriate but explain them if they are complex.
    """.trimIndent()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(content = text, isUser = true)
        _messages.value = _messages.value + userMessage

        viewModelScope.launch {
            _isAiThinking.value = true
            try {
                val fullPrompt = "$systemPrompt\n\nUser: $text\nAssistant:"
                val response = aiProviderManager.generateAnalysis(fullPrompt)
                _messages.value = _messages.value + ChatMessage(content = response, isUser = false)
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    content = "⚠️ Error: ${e.message}. Please check your connection or AI settings.",
                    isUser = false
                )
            } finally {
                _isAiThinking.value = false
            }
        }
    }

    fun clearChat() {
        _messages.value = listOf(
            ChatMessage(
                content = "Hello! I'm Mekanik AI, your Automotive Assistant. Ask me anything about vehicle diagnostics, repairs, maintenance, or automotive systems.",
                isUser = false
            )
        )
    }

    companion object {
        class Factory(
            private val application: Application,
            private val aiProviderManager: AIProviderManager
        ) : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AutomotiveChatViewModel(application, aiProviderManager) as T
            }
        }
    }
}

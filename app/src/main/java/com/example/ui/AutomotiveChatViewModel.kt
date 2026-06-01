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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String? = null
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
    }

    fun savePosition() {
        prefs.edit {
            putFloat("chat_icon_x", posX)
            putFloat("chat_icon_y", posY)
            apply()
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

        CRITICAL RESPONSE RULES:
        1. PLAIN TEXT ONLY: Do not use Markdown, bold (**), italics (*), or code blocks.
        2. NO SYMBOLS: Never use symbols like *, #, _, or ~ for formatting or lists.
        3. NATURAL FLOW: Use plain numbers (1, 2, 3) for steps or simply separate items with clear paragraphs.
        4. NO LABELS: Do not start your response with "Assistant:" or "Mekanik AI:".
        5. DOMAIN LOCK: If the user asks about non-automotive topics, politely state: "I am specialized exclusively for automotive topics. How can I help you with your vehicle today?"
        6. PROFESSIONAL TONE: Be helpful, clear, and professional, like an expert mechanic speaking to a customer.
    """.trimIndent()

    /**
     * Sanitizes the AI response by removing Markdown formatting, prohibited symbols,
     * and technical artifacts while preserving natural paragraph structure.
     */
    private fun sanitizeResponse(text: String): String {
        return text
            // 1. Remove Markdown markers
            .replace(markdownRegex, "")
            // 2. Remove Headers
            .replace(headerRegex, "")
            // 3. Remove Link markers but keep text: [text](url) -> text
            .replace(linkRegex, "$1")
            // 4. Remove image markers: ![alt](url) -> ""
            .replace(imageRegex, "")
            // 5. Remove blockquote markers at start of lines
            .replace(quoteRegex, "")
            // 6. Remove HTML/XML tags
            .replace(htmlRegex, "")
            // 7. Remove common AI prefix labels
            .replace(labelRegex, "")
            // 8. Collapse excessive newlines (3 or more -> 2)
            .replace(newlineRegex, "\n\n")
            // 9. Trim whitespace from each line and overall
            .lines().joinToString("\n") { it.trim() }.trim()
    }

    private val _selectedImageUris = MutableStateFlow<List<String>>(emptyList())
    val selectedImageUris: StateFlow<List<String>> = _selectedImageUris.asStateFlow()

    fun onImagesSelected(uris: List<String>) {
        _selectedImageUris.value = (_selectedImageUris.value + uris).distinct()
    }

    fun removeImage(uri: String) {
        _selectedImageUris.value = _selectedImageUris.value.filter { it != uri }
    }

    private suspend fun uriToBase64(uriString: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val contentResolver = getApplication<Application>().contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(android.net.Uri.parse(uriString))
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return@withContext null

            // Resize if too large (Hugging Face / Gemma might have limits)
            val maxDimension = 1024
            val scale = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val widthScale = maxDimension.toFloat() / bitmap.width
                val heightScale = maxDimension.toFloat() / bitmap.height
                kotlin.math.min(widthScale, heightScale)
            } else 1f

            val finalBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else bitmap

            val outputStream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun sendMessage(text: String, imageUris: List<String> = _selectedImageUris.value) {
        if (text.isBlank() && imageUris.isEmpty()) return

        val userMessage = ChatMessage(
            content = text,
            isUser = true,
            imageUri = imageUris.firstOrNull() // For display in bubble, we'll just show the first one or we can update bubble
        )
        _messages.value = _messages.value + userMessage
        _selectedImageUris.value = emptyList() // Clear after sending

        viewModelScope.launch {
            _isAiThinking.value = true
            
            val aiMessageIndex = _messages.value.size
            _messages.value = _messages.value + ChatMessage(content = "", isUser = false)
            
            try {
                val contentList = mutableListOf<com.example.service.CloudAiContent>()
                
                if (text.isNotBlank()) {
                    contentList.add(com.example.service.CloudAiContent(type = "text", text = text))
                }

                imageUris.forEach { uri ->
                    val base64 = uriToBase64(uri)
                    if (base64 != null) {
                        contentList.add(
                            com.example.service.CloudAiContent(
                                type = "image_url",
                                imageUrl = com.example.service.CloudAiImageUrl(url = "data:image/jpeg;base64,$base64")
                            )
                        )
                    }
                }

                val promptPayload: Any = if (contentList.size > 1 || (contentList.size == 1 && contentList[0].type == "image_url")) {
                    // Prepend system prompt as a text item
                    listOf(com.example.service.CloudAiContent(type = "text", text = systemPrompt)) + contentList
                } else {
                    "$systemPrompt\n\nUser: $text\nAssistant:"
                }

                var accumulatedResponse = ""
                
                aiProviderManager.generateStreamingAnalysis(promptPayload).collect { chunk ->
                    accumulatedResponse += chunk
                    
                    val sanitized = withContext(Dispatchers.Default) {
                        sanitizeResponse(accumulatedResponse)
                    }
                    
                    withContext(Dispatchers.Main) {
                        val currentMessages = _messages.value.toMutableList()
                        if (aiMessageIndex < currentMessages.size) {
                            currentMessages[aiMessageIndex] = currentMessages[aiMessageIndex].copy(
                                content = sanitized
                            )
                            _messages.value = currentMessages
                        }
                    }
                }
            } catch (e: Exception) {
                // error handling...
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
        // Pre-compiled Regex for performance
        private val markdownRegex = Regex("""\*\*|\*|`|___|__|~~""")
        private val headerRegex = Regex("""#+\s""")
        private val linkRegex = Regex("""\[(.*?)\]\(.*?\)""")
        private val imageRegex = Regex("""!\[.*?\]\(.*?\)""")
        private val quoteRegex = Regex("""^>\s*""", RegexOption.MULTILINE)
        private val htmlRegex = Regex("""<[^>]*>""")
        private val labelRegex = Regex("""^(Assistant|Mekanik AI|AI|System|User|Prompt):\s*""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        private val newlineRegex = Regex("""\n{3,}""")

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

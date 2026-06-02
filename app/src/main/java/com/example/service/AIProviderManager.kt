package com.example.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.system.measureTimeMillis

class AIProviderManager(
    private val context: Context,
    val settingsManager: SettingsManager,
    private val networkMonitor: NetworkMonitor
) {
    private val TAG = "AIProviderManager"

    private val _onlineConnectionStatus = MutableStateFlow("Disconnected")
    val onlineConnectionStatus: StateFlow<String> = _onlineConnectionStatus.asStateFlow()

    private val _apiHealthStatus = MutableStateFlow("Unknown")
    val apiHealthStatus: StateFlow<String> = _apiHealthStatus.asStateFlow()

    private val _responseLatency = MutableStateFlow("N/A")
    val responseLatency: StateFlow<String> = _responseLatency.asStateFlow()

    init {
        // Track connection
        val initialInternet = networkMonitor.isInternetAvailable.value
        _onlineConnectionStatus.value = if (initialInternet) "Online" else "No Connection"
        _apiHealthStatus.value = if (initialInternet) "Healthy" else "Unavailable"
    }

    suspend fun generateAnalysis(prompt: Any): String {
        var fullResponse = ""
        generateStreamingAnalysis(prompt).collect { chunk ->
            fullResponse += chunk
        }
        return fullResponse
    }

    fun generateStreamingAnalysis(prompt: Any): Flow<String> = flow {
        val mode = settingsManager.aiMode.value
        val isInternet = networkMonitor.isInternetAvailable.value

        if (mode == AiMode.ONLINE) {
            if (!isInternet) {
                _onlineConnectionStatus.value = "No Connection"
                _apiHealthStatus.value = "Unavailable"
                throw Exception("Internet offline. Online mode requires connectivity.")
            }

            _onlineConnectionStatus.value = "Online"
            val activeModel = settingsManager.preferredOnlineModel.value

            val messages = if (prompt is List<*>) {
                listOf(CloudAiMessage(role = "user", content = prompt as List<Any>))
            } else {
                listOf(CloudAiMessage(role = "user", content = prompt.toString()))
            }

            val request = CloudAiRequest(
                model = activeModel,
                messages = messages,
                stream = true
            )

            val okHttpClient = CloudAiClient.getOkHttpClient()
            val okHttpRequest = CloudAiClient.createStreamRequest(request)
            val moshi = CloudAiClient.getMoshi()
            val responseAdapter = moshi.adapter(CloudAiResponse::class.java)

            val startMillis = System.currentTimeMillis()
            
            try {
                okHttpClient.newCall(okHttpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        val keyHint = CloudAiClient.getApiKey().take(7)
                        throw IOException("Cloud API error ${response.code}: $errorBody (Model: $activeModel, Key: $keyHint...)")
                    }

                    val reader = response.body?.source() ?: throw IOException("Empty response body")
                    
                    while (!reader.exhausted()) {
                        val line = reader.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val data = line.substring(6).trim()
                            if (data == "[DONE]") break
                            
                            try {
                                val chunk = responseAdapter.fromJson(data)
                                val content = chunk?.choices?.firstOrNull()?.delta?.content as? String
                                if (content != null) {
                                    emit(content)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing stream chunk: $data", e)
                            }
                        }
                    }
                    _apiHealthStatus.value = "Healthy"
                    _responseLatency.value = "${System.currentTimeMillis() - startMillis}ms"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cloud streaming query failed", e)
                _apiHealthStatus.value = "Error: ${e.message}"
                throw e
            }
        } else {
            // OFFLINE MODE (Native GGUF Streaming)
            val textPrompt = when (prompt) {
                is String -> prompt
                is List<*> -> {
                    prompt.filterIsInstance<CloudAiContent>()
                        .mapNotNull { it.text }
                        .joinToString("\n\n")
                }
                else -> prompt.toString()
            }
            
            if (textPrompt.isBlank()) {
                throw Exception("Offline AI requires text input.")
            }

            // Attempt to initialize preferred model
            val selectedId = settingsManager.preferredOfflineModelId.value ?: "llama-3.2-1b"
            val modelFileName = getFileNameForModelId(selectedId)
            val file = File(context.filesDir, modelFileName)

            if (!file.exists()) {
                throw Exception("Local model data missing. Please download an offline model in Settings.")
            }

            if (LlamaService.getInitializedPath() != file.absolutePath) {
                LlamaService.initialize(file.absolutePath).onFailure { 
                    throw Exception("Failed to initialize Llama: ${it.message}")
                }
            }

            val startMillis = System.currentTimeMillis()
            var firstToken = true

            LlamaService.generateText(textPrompt).collect { chunk ->
                if (firstToken) {
                    _responseLatency.value = "${System.currentTimeMillis() - startMillis}ms"
                    firstToken = false
                }
                emit(chunk)
            }
        }
    }.flowOn(Dispatchers.IO)

    // Removed generateAnalysisSync as it is no longer needed with direct streaming

    private fun getFileNameForModelId(id: String): String {
        return when (id) {
            "llama-3.2-1b" -> "llama-3.2-1b-instruct-q8_0.gguf"
            "smollm2-1.7b" -> "smollm2-1.7b-instruct-q8_0.gguf"
            "qwen2.5-1.5b" -> "qwen2.5-1.5b-instruct-q8_0.gguf"
            else -> "llama-3.2-1b-instruct-q8_0.gguf"
        }
    }
}

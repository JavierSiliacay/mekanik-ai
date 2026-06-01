package com.example.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
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

    suspend fun generateAnalysis(prompt: String): String = withContext(Dispatchers.IO) {
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
            val authHeader = "Bearer ${OpenRouterClient.getApiKey()}"
            val referer = "https://ai.studio/build/mekanik"
            val title = "Mekanik AI"

            val request = OpenRouterRequest(
                model = activeModel,
                messages = listOf(
                    OpenRouterMessage(role = "user", content = prompt)
                )
            )

            var responseText: String? = null
            val timeTaken = measureTimeMillis {
                try {
                    val apiResponse = OpenRouterClient.service.generateCompletion(
                        authorization = authHeader,
                        referer = referer,
                        title = title,
                        request = request
                    )
                    responseText = apiResponse.choices?.firstOrNull()?.message?.content
                    _apiHealthStatus.value = "Healthy"
                } catch (e: Exception) {
                    Log.e(TAG, "Cloud API query failed", e)
                    _apiHealthStatus.value = "Error: ${e.message}"
                    throw Exception("Cloud API Failed: ${e.message}")
                }
            }

            _responseLatency.value = "${timeTaken}ms"
            return@withContext responseText ?: throw Exception("Empty response received from Cloud API.")

        } else {
            // OFFLINE MODE
            // Attempt to initialize preferred model if not initialized
            val selectedId = settingsManager.preferredOfflineModelId.value ?: "gemma-2b"
            val modelFileName = getFileNameForModelId(selectedId)
            val file = File(context.filesDir, modelFileName)

            if (!file.exists()) {
                throw Exception("Local model data missing. Please download an offline model in Settings.")
            }

            // Lazy initialize / reload if paths differ
            if (MediaPipeLlmInferenceService.getInitializedPath() != file.absolutePath) {
                val initResult = MediaPipeLlmInferenceService.initialize(context, file.absolutePath)
                if (initResult.isFailure) {
                    throw Exception("Failed to bind local device buffers: ${initResult.exceptionOrNull()?.message}")
                }
            }

            val startMillis = System.currentTimeMillis()
            val text = MediaPipeLlmInferenceService.generateText(prompt)
            val stopMillis = System.currentTimeMillis()
            _responseLatency.value = "${stopMillis - startMillis}ms"

            return@withContext text
        }
    }

    private fun getFileNameForModelId(id: String): String {
        return when (id) {
            "gemma-2b" -> "gemma-2b-it-cpu-int4.bin"
            "llama-3.2" -> "llama-3.2-1b-it-cpu-int4.bin"
            "phi-3-mini" -> "phi-3-mini-4k-instruct-cpu-int4.bin"
            else -> "gemma-2b-it-cpu-int4.bin"
        }
    }
}

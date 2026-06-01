package com.example.service

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.io.FileNotFoundException

object MediaPipeLlmInferenceService {
    private const val TAG = "MediaPipeLlm"
    private var llmInference: LlmInference? = null
    private var initializedModelPath: String? = null

    fun getDefaultModelPath(context: Context): String {
        // Updated to match ModelDownloadManager's production GGUF filename
        return File(context.filesDir, "gemma-2b-it.Q4_K_M.gguf").absolutePath
    }

    fun isInitialized(): Boolean {
        return llmInference != null
    }

    fun getInitializedPath(): String? {
        return initializedModelPath
    }

    fun initialize(context: Context, modelPath: String): Result<Unit> {
        try {
            if (llmInference != null && initializedModelPath == modelPath) {
                return Result.success(Unit)
            }

            // Close existing
            close()

            val file = File(modelPath)
            if (!file.exists()) {
                return Result.failure(FileNotFoundException("Model file not found at: $modelPath"))
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setTemperature(0.7f)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            initializedModelPath = modelPath
            Log.d(TAG, "MediaPipe LlmInference initialized successfully with path: $modelPath")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe LlmInference", e)
            return Result.failure(e)
        }
    }

    fun generateText(prompt: String): String {
        val inferenceInstance = llmInference ?: return "Local LlmInference not initialized. Please load model in Settings."
        return try {
            inferenceInstance.generateResponse(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            "Error running on-device inference: ${e.message}"
        }
    }

    fun close() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LlmInference", e)
        } finally {
            llmInference = null
            initializedModelPath = null
        }
    }
}

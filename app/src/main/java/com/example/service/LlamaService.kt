package com.example.service

import android.util.Log
import org.codeshipping.llamakotlin.LlamaModel
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * LlamaService provides native GGUF support using the CodeShipping llama-kotlin-android library.
 */
object LlamaService {
    private const val TAG = "LlamaService"
    private var llamaModel: LlamaModel? = null
    private var initializedModelPath: String? = null

    fun isInitialized(): Boolean = llamaModel != null

    fun getInitializedPath(): String? = initializedModelPath

    /**
     * Initializes the Llama model. Now using suspend to match the new library's API.
     */
    suspend fun initialize(modelPath: String): Result<Unit> {
        return try {
            if (llamaModel != null && initializedModelPath == modelPath) {
                return Result.success(Unit)
            }

            close()

            val file = File(modelPath)
            if (!file.exists()) {
                throw FileNotFoundException("Model GGUF file not found: $modelPath")
            }

            // Using LlamaModel from the CodeShipping library
            // We set threads to 8 and context to 2048 for optimal mobile performance
            llamaModel = LlamaModel.load(modelPath) {
                contextSize = 2048
                threads = 8
            }
            
            initializedModelPath = modelPath
            Log.d(TAG, "llama.cpp model initialized successfully: $modelPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize llama.cpp", e)
            Result.failure(e)
        }
    }

    /**
     * Generates text using the library's native Flow support.
     */
    fun generateText(prompt: String): Flow<String> {
        val model = llamaModel 
        if (model == null) {
            Log.e(TAG, "Llama model not initialized.")
            return emptyFlow()
        }
        
        // Formatted prompt for Llama 3.2 GGUF
        val formattedPrompt = "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n\n$prompt<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
        
        return model.generateStream(formattedPrompt)
    }

    /**
     * Closes the model and releases native resources.
     */
    fun close() {
        try {
            llamaModel?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing llama model", e)
        } finally {
            llamaModel = null
            initializedModelPath = null
        }
    }
}

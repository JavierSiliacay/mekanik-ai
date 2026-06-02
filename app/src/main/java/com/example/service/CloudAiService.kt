package com.example.service

import com.example.BuildConfig
import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// Support for both simple text and multimodal content
data class CloudAiContent(
    val type: String,
    val text: String? = null,
    @Json(name = "image_url") val imageUrl: CloudAiImageUrl? = null
)

data class CloudAiImageUrl(val url: String)

data class CloudAiMessage(
    val role: String? = null,
    val content: Any? = null // Can be String or List<CloudAiContent>
)

data class CloudAiRequest(
    val model: String,
    val messages: List<CloudAiMessage>,
    val stream: Boolean = false
)

data class CloudAiChoice(
    val message: CloudAiMessage? = null,
    val delta: CloudAiMessage? = null, // Used in streaming
    @Json(name = "finish_reason") val finishReason: String? = null
)

data class CloudAiResponse(val choices: List<CloudAiChoice>?)

data class RemoteConfig(
    @Json(name = "hf_api_key") val hfApiKey: String,
    @Json(name = "hf_model") val hfModel: String
)

interface CloudAiApiService {
    @POST("chat/completions")
    suspend fun generateCompletion(
        @Header("Authorization") authorization: String,
        @Body request: CloudAiRequest
    ): CloudAiResponse

    @retrofit2.http.GET
    suspend fun fetchRemoteConfig(@retrofit2.http.Url url: String): RemoteConfig
}

object CloudAiClient {
    private const val BASE_URL = "https://router.huggingface.co/v1/"
    private val CONFIG_URL = BuildConfig.CONFIG_URL

    private var currentRemoteConfig: RemoteConfig? = null

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: CloudAiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CloudAiApiService::class.java)
    }

    suspend fun refreshConfig() {
        if (CONFIG_URL.isBlank()) {
            Log.w("CloudAiClient", "CONFIG_URL is blank. Check your .env file and rebuild.")
            return
        }
        try {
            Log.d("CloudAiClient", "Fetching remote config from: $CONFIG_URL")
            val config = service.fetchRemoteConfig(CONFIG_URL)
            if (config.hfApiKey.isNotBlank()) {
                currentRemoteConfig = config
                Log.d("CloudAiClient", "Remote config updated successfully. Key starts with: ${config.hfApiKey.take(7)}...")
            } else {
                Log.e("CloudAiClient", "Fetched config has an empty API key")
            }
        } catch (e: Exception) {
            Log.e("CloudAiClient", "Failed to update remote config: ${e.message}", e)
        }
    }

    fun getApiKey(): String {
        return currentRemoteConfig?.hfApiKey?.trim() ?: BuildConfig.HF_API_KEY.trim()
    }

    fun getModel(): String {
        return currentRemoteConfig?.hfModel?.trim() ?: BuildConfig.HF_MODEL.trim()
    }

    // Direct OkHttp access for streaming SSE
    fun createStreamRequest(request: CloudAiRequest): Request {
        val json = moshi.adapter(CloudAiRequest::class.java).toJson(request)
        return Request.Builder()
            .url("${BASE_URL}chat/completions")
            .header("Authorization", "Bearer ${getApiKey()}")
            .header("Content-Type", "application/json")
            .post(json.toRequestBody())
            .build()
    }

    fun getOkHttpClient() = okHttpClient
    fun getMoshi() = moshi
}

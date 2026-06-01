package com.example.service

import com.example.BuildConfig
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
    val role: String,
    val content: Any // Can be String or List<CloudAiContent>
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

interface CloudAiApiService {
    @POST("chat/completions")
    suspend fun generateCompletion(
        @Header("Authorization") authorization: String,
        @Body request: CloudAiRequest
    ): CloudAiResponse
}

object CloudAiClient {
    private const val BASE_URL = "https://router.huggingface.co/v1/"

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

    fun getApiKey(): String {
        return BuildConfig.HF_API_KEY
    }

    fun getModel(): String {
        return BuildConfig.HF_MODEL
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

package com.example.service

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AiMode {
    ONLINE,
    OFFLINE
}

class SettingsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("mekanik_settings_manager", Context.MODE_PRIVATE)

    private val _aiMode = MutableStateFlow(
        AiMode.valueOf(prefs.getString("ai_mode", AiMode.ONLINE.name) ?: AiMode.ONLINE.name)
    )
    val aiMode: StateFlow<AiMode> = _aiMode.asStateFlow()

    private val _preferredOnlineModel = MutableStateFlow(
        prefs.getString("preferred_online_model", BuildConfig.OPENROUTER_MODEL) ?: BuildConfig.OPENROUTER_MODEL
    )
    val preferredOnlineModel: StateFlow<String> = _preferredOnlineModel.asStateFlow()

    private val _preferredOfflineModelId = MutableStateFlow<String?>(
        prefs.getString("preferred_offline_model_id", "gemma-2b") ?: "gemma-2b"
    )
    val preferredOfflineModelId: StateFlow<String?> = _preferredOfflineModelId.asStateFlow()

    fun setAiMode(mode: AiMode) {
        prefs.edit().putString("ai_mode", mode.name).apply()
        _aiMode.value = mode
    }

    fun setPreferredOnlineModel(model: String) {
        prefs.edit().putString("preferred_online_model", model).apply()
        _preferredOnlineModel.value = model
    }

    fun setPreferredOfflineModelId(id: String?) {
        prefs.edit().putString("preferred_offline_model_id", id).apply()
        _preferredOfflineModelId.value = id
    }
}

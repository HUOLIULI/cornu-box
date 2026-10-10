package com.aggregator.shell.core.common

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * TTS 相关 DataStore 键（云端 TTS 端点 / 凭据 / 音色，仅存本机）。
 */
object TtsConfigKeys {
    val BASE_URL = stringPreferencesKey("tts_base_url")
    val API_KEY = stringPreferencesKey("tts_api_key")
    val VOICE = stringPreferencesKey("tts_voice")
    val MODEL = stringPreferencesKey("tts_model")
}

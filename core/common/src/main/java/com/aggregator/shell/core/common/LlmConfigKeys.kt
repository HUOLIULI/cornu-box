package com.aggregator.shell.core.common

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore preference keys for LLM and other user-configurable services.
 * Values are entered in Settings and never read from the build environment.
 */
object LlmConfigKeys {
    val BASE_URL  = stringPreferencesKey("llm_base_url")
    val API_KEY   = stringPreferencesKey("llm_api_key")
    val MODEL     = stringPreferencesKey("llm_model")

    val DANMAKU_BASE_URL = stringPreferencesKey("danmaku_base_url")
    val DANMAKU_API_KEY  = stringPreferencesKey("danmaku_api_key")
}

package com.aggregator.shell.core.source.sandbox

sealed class JsResult {
    data class Success(val value: String) : JsResult()
    data class Failure(val error: Throwable) : JsResult()
    object Timeout : JsResult()
}

/**
 * JS execution sandbox. Implementations must enforce a timeout, expose no
 * filesystem access, and route all network calls through the provided client.
 */
interface JsSandboxExecutor {
    suspend fun execute(
        script: String,
        bindings: Map<String, Any> = emptyMap(),
        timeoutMillis: Long = 10_000
    ): JsResult
}

package com.aggregator.shell.core.media.player

/**
 * URL scheme allowlist for media playback. Blocks `file://` and other
 * dangerous schemes that could read private files or exfiltrate data.
 */
object PlayUrlValidator {
    private val ALLOWED = setOf("http", "https", "rtmp", "rtsp", "mms")

    fun validate(url: String): Boolean {
        val scheme = runCatching {
            android.net.Uri.parse(url).scheme?.lowercase() ?: ""
        }.getOrDefault("")
        return scheme in ALLOWED || scheme.isEmpty()
    }
}

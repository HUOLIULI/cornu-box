package com.aggregator.shell.core.media.player

object PlayUrlValidator {

    /**
     * Allowlist of URL schemes permitted for media playback.
     * `file://`, `content://`, `content-external://`, `jar://` and other local/exfil
     * schemes are blocked. The allowlist is intentionally restrictive; users who
     * need additional protocols should extend it via a subclass.
     */
    private val ALLOWED = setOf("http", "https", "rtmp", "rtsp", "mms")

    /**
     * Validate whether [url] uses a permitted media scheme.
     *
     * A bare path (no scheme) is treated as a relative URL and rejected, since
     * ExoPlayer requires an absolute URI with a known scheme.
     */
    fun validate(url: String): Boolean {
        val scheme = runCatching {
            android.net.Uri.parse(url).scheme?.lowercase()
        }.getOrNull()
        return scheme in ALLOWED
    }
}

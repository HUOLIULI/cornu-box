package com.aggregator.shell.core.media.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * 播放韧性判定（对标 PeekPro 播放层，纯 Kotlin 实现，无 native）：
 * 直播窗口识别、网页误返回识别、DASH/HLS 回退策略。
 */
object PlaybackResilience {

    /** PeekPro 风格：URL 特征自动识别是否直播流（HLS .m3u8 / live 路径 / 直播参数）。 */
    fun isLiveLike(url: String): Boolean =
        url.contains(".m3u8", true) ||
            url.contains("/live/", true) ||
            url.contains("hls_", true) ||
            url.contains("live=", true) ||
            url.contains("is-live", true)

    /**
     * PeekPro 风格：识别"服务器返回网页而非视频流"（text/html 或含 <html 特征）。
     * 命中时上层应走多线路/重试而非继续喂给 ExoPlayer。
     */
    fun looksLikeHtml(body: String): Boolean {
        val t = body.take(4096).lowercase()
        return t.contains("<html") || t.contains("<!doctype html") ||
            t.contains("<body") || t.contains("text/html")
    }

    /** PeekPro 风格：直播 HLS 播放列表掉出直播窗口时，建议回退重拉（返回新 URL 提示）。 */
    fun liveWindowRetryHint(url: String, attempt: Int): Boolean =
        isLiveLike(url) && attempt in 1..3

    /** PeekPro 风格：DASH/虚拟流首轮失败时，回退"系统音频 renderer"即普通 Progressive 模式。 */
    fun preferProgressiveOnFail(url: String, wasDash: Boolean): Boolean =
        wasDash && !url.contains(".m3u8", true)
}

/**
 * PeekPro 风格：SSL 证书绕过 OkHttp 客户端（用于证书不受信任的视频源）。
 * 仅对本应用播放网络层生效，不改变 ExoPlayer 解码。
 */
object SslBypassClient {

    /** 构建一个信任所有证书 + 不校验主机名的 OkHttp 客户端（仅用于用户主动选用的源）。 */
    fun build(): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = runCatching {
            SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        }.getOrNull() ?: return OkHttpClient()
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * 多线路/重试策略（对标 PeekPro "线路切换"）。给定候选线路，按指数退避择一重试，
 * 全部失败返回 null 让 UI 提示。纯逻辑，便于单测。
 */
object LineRetryPolicy {
    fun nextDelayMs(attempt: Int, baseMs: Long = 1000L, capMs: Long = 15000L): Long =
        (baseMs shl attempt).coerceAtMost(capMs)

    fun shouldRetry(attempt: Int, maxAttempts: Int): Boolean = attempt < maxAttempts
}

/**
 * PeekPro 风格播放前预检（HTTP 层，纯 JVM + OkHttp，无 native）。
 *
 * 对候选播放 URL 发一个带 `Range: bytes=0-0` 的 GET，快速判定：
 * - 成功且 `Content-Type` 非 HTML → 可用
 * - 响应体含 HTML 特征（[PlaybackResilience.looksLikeHtml]）→ 该线路"返回网页而非流"，
 *   上层应切下一线路
 * - 4xx/5xx → 该线路不可用
 *
 * 仅用于"切线路前的预筛"，不改变 ExoPlayer 实际解码。默认 8s 超时，
 * 失败/网络异常时返回 `true`（保守放行，避免误杀可用线路）。
 */
object PlayUrlPreflight {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** @return 是否可用（true=放行，false=建议切下一线路）。 */
    suspend fun check(url: String, headers: Map<String, String> = emptyMap()): Boolean {
        if (url.isBlank() || !url.startsWith("http")) return true
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.addHeader(k, v) }
            // Range 请求避免拉整段流；很多源不支持时退回普通 GET
            builder.addHeader("Range", "bytes=0-0")
            runCatching {
                client.newCall(builder.build()).execute().use { r ->
                    if (r.code in 200..299) {
                        val ctype = r.header("Content-Type").orEmpty().lowercase()
                        if (ctype.contains("html")) return@withContext false
                        // 读一小段判断是否 HTML 正文（最多 4KB）
                        val buf = okio.Buffer()
                        r.body?.source()?.readByteArray(4096)?.let { buf.write(it) }
                        val text = buf.readUtf8().lowercase()
                        if (PlaybackResilience.looksLikeHtml(text)) return@withContext false
                        return@withContext true
                    }
                    return@withContext r.code in 200..299
                }
            }.getOrDefault(true)
        }
    }

    /** 从候选线路中挑第一条可用；全不可用返回 null。 */
    suspend fun firstAvailable(candidates: List<Pair<String, Map<String, String>>>): Int? {
        candidates.forEachIndexed { i, (url, headers) ->
            if (check(url, headers)) return i
        }
        return null
    }
}

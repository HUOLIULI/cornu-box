package com.aggregator.shell.core.media.player

import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
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

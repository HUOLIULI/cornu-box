package com.aggregator.shell.core.source.gateway

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 本地脚本网关（聚合自 DsPlayer/PeekPro 的 T4 / PHP 本地服务形态）。
 *
 * 在 127.0.0.1 起一个 HTTP 服务，把影视源 / drpy 脚本里的跨域请求统一代理出去：
 *   GET /proxy?url=<encoded真实地址>
 *
 * 脚本与 WebView 里只写 `http://127.0.0.1:<port>/proxy?url=...`，网关负责：
 *  - 注入桌面 UA / Referer（解决源站反爬）
 *  - 把 https / http 明文请求收敛到一个出口（解决 targetSdk35 明文限制）
 *  - 统一错误页为 JSON，便于脚本侧 try/catch
 *
 * 端口由系统分配（0 = 随机可用端口），启动后通过 [port] 暴露给脚本层。
 */
class LocalGateway(
    private val client: OkHttpClient = defaultClient,
    port: Int = 0
) : NanoHTTPD("127.0.0.1", port) {

    @Volatile
    var gatewayPort: Int = 0
        private set

    val baseUrl: String get() = "http://127.0.0.1:$gatewayPort"

    override fun start() {
        super.start(SOCKET_READ_TIMEOUT, false)
        gatewayPort = super.getPort()
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when (session.uri) {
                "/ping" -> json(Response.Status.OK, """{"ok":true,"port":$gatewayPort}""")
                "/proxy" -> handleProxy(session)
                else -> json(Response.Status.NOT_FOUND, """{"error":"unknown route ${session.uri}"}""")
            }
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, """{"error":"${e.message}"}""")
        }
    }

    private fun handleProxy(session: IHTTPSession): Response {
        val target = session.parms["url"]
            ?: return json(Response.Status.BAD_REQUEST, """{"error":"missing url param"}""")
        val ua = session.parms["ua"] ?: DEFAULT_UA
        val referer = session.parms["referer"]

        val builder = Request.Builder().url(target).header("User-Agent", ua)
        if (!referer.isNullOrBlank()) builder.header("Referer", referer)

        return try {
            client.newCall(builder.build()).execute().use { resp ->
                val body = resp.body?.bytes() ?: ByteArray(0)
                val contentType = resp.header("Content-Type", "application/octet-stream")
                newFixedLengthResponse(
                    if (resp.isSuccessful) Response.Status.OK else Response.Status.lookup(resp.code, Response.Status.INTERNAL_ERROR),
                    contentType,
                    body.inputStream(),
                    body.size.toLong()
                )
            }
        } catch (e: IOException) {
            json(Response.Status.BAD_GATEWAY, """{"error":"upstream unreachable: ${e.message}"}""")
        }
    }

    private fun json(status: Response.Status, payload: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", payload)

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        /** 脚本侧把真实地址包成本地网关 URL。 */
        fun wrap(baseUrl: String, realUrl: String): String =
            "$baseUrl/proxy?url=${java.net.URLEncoder.encode(realUrl, "UTF-8")}"
    }
}

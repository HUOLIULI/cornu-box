package com.aggregator.shell.core.source.sandbox

import java.util.concurrent.ConcurrentHashMap

/**
 * drpy3 / TVBox 脚本宿主绑定（聚合自 DsPlayer 的 QuickJS 运行环境）。
 *
 * 脚本里可直接调用：
 *   req(url, options?) -> { status, content, headers }
 *   localStorage.getItem / setItem
 *
 * 与 [JsSandboxExecutor] 解耦：宿主函数由 Kotlin 侧实现，脚本只声明式使用；
 * 当前 Rhino 实现把这些绑定作为 JS 变量注入 scope。QuickJS 接入后绑定不变。
 */
object Drpy3Host {

    private val storage = ConcurrentHashMap<String, String>()

    /** 注入到 JS scope 的全局变量名。 */
    const val BIND_REQ = "req"
    const val BIND_LOCALSTORAGE = "localStorage"

    data class HttpResponse(
        val status: Int,
        val content: String,
        val headers: Map<String, String> = emptyMap()
    )

    /**
     * 同步请求（脚本语义）。真实网络由上层引擎用 OkHttp 完成；
     * 这里只做绑定形状，实际 HTTP 在引擎内执行，避免 JS 沙箱直连网络。
     */
    fun interface ReqFunction {
        fun call(url: String, options: Map<String, String> = emptyMap()): HttpResponse
    }

    fun storageGet(key: String): String? = storage[key]
    fun storageSet(key: String, value: String) { storage[key] = value }
    fun storageRemove(key: String) { storage.remove(key) }
    fun clear() = storage.clear()
}

/**
 * 脚本执行结果协议（与 DsPlayer system_prompt 对齐）：
 * is_success / error_msg / verified_data{ result, log }
 */
data class ScriptRunResult(
    val isSuccess: Boolean,
    val errorMsg: String? = null,
    val result: String? = null,
    val log: String = ""
)

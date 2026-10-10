package com.aggregator.shell.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 写源助手的工具执行器（聚合自 DsPlayer 内置编程助手的 run_script 协议）。
 *
 * LLM 通过这个工具在本机安全地跑脚本：探测网关、试接口、生成并调试源。
 * 结果统一包成 [ScriptRunResult]，由 [AiSourceAssistant] 回灌给 LLM 形成闭环：
 * 失败 → 读 error_msg 修正 → 重跑，直到成功或如实汇报。
 *
 * JS 执行器由上层注入（避免 core:ai 反向依赖 core:source）；
 * shell 只放行本机/局域网只读命令，禁止管道/重定向/多命令。
 */
class ScriptRunnerTool(
    private val jsExecutor: suspend (String) -> String
) {
    sealed interface Lang {
        data object Js : Lang
        data object Shell : Lang
    }

    companion object {
        private val ALLOWED_SHELL = setOf("ls", "cat", "echo", "ping", "curl", "pwd")
        private const val MAX_OUTPUT = 64 * 1024
        private const val SHELL_TIMEOUT_MS = 10_000L
    }

    suspend fun run(lang: Lang, code: String): ScriptRunResult = withContext(Dispatchers.IO) {
        when (lang) {
            is Lang.Js -> runCatching {
                val out = jsExecutor(code)
                ScriptRunResult(isSuccess = true, result = out)
            }.getOrElse { ScriptRunResult(isSuccess = false, errorMsg = it.message) }

            is Lang.Shell -> runShell(code.trim())
        }
    }

    private fun runShell(command: String): ScriptRunResult {
        if (anyUnsafe(command)) return ScriptRunResult(
            isSuccess = false,
            errorMsg = "shell 仅允许单条只读命令（${ALLOWED_SHELL.joinToString("/")}），禁止管道/重定向/多命令"
        )
        val bin = command.substringBefore(' ').lowercase()
        if (bin !in ALLOWED_SHELL) return ScriptRunResult(
            isSuccess = false, errorMsg = "不允许的命令: $bin"
        )
        return runCatching {
            val proc = ProcessBuilder(*command.split(" ").toTypedArray())
                .redirectErrorStream(true)
                .start()
            if (!proc.waitFor(SHELL_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                proc.destroy()
                return ScriptRunResult(isSuccess = false, errorMsg = "执行超时")
            }
            val out = proc.inputStream.bufferedReader().readText().take(MAX_OUTPUT)
            ScriptRunResult(isSuccess = true, result = out)
        }.getOrElse { ScriptRunResult(isSuccess = false, errorMsg = it.message) }
    }

    private fun anyUnsafe(cmd: String): Boolean =
        cmd.contains("|") || cmd.contains(">") || cmd.contains("<") ||
            cmd.contains("&&") || cmd.contains(";") || cmd.contains("`") ||
            cmd.contains("rm ") || cmd.contains("sudo")
}

/** 与 DsPlayer system_prompt 对齐的结果协议。 */
data class ScriptRunResult(
    val isSuccess: Boolean,
    val errorMsg: String? = null,
    val result: String? = null,
    val log: String = ""
)

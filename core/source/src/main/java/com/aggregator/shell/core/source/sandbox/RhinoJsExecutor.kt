package com.aggregator.shell.core.source.sandbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import java.util.concurrent.atomic.AtomicLong

/**
 * 死循环脚本触达指令上限时抛出的超时信号。
 */
class RhinoTimeoutException : RuntimeException("JS 执行超过指令上限")

/**
 * Rhino-backed JS sandbox（纯 JVM，无 NDK），默认构建即可编译。
 *
 * 超时通过 Rhino 指令计数（[ContextFactory.observeInstructionCount]）强制执行：
 * 死循环脚本会在累计执行超过 [instructionLimit] 条指令时抛出 [RhinoTimeoutException]，
 * 由本类转换为 [JsResult.Timeout]。
 *
 * 相比旧实现的 thread.interrupt 方案：JS 死循环无法被 interrupt 打断，
 * 被中断的线程会永久泄漏；指令计数方案在解释器内部终止，无线程泄漏。
 */
class RhinoJsExecutor(
    private val instructionLimit: Long = 5_000_000L
) : JsSandboxExecutor {

    override suspend fun execute(
        script: String,
        bindings: Map<String, Any>,
        timeoutMillis: Long
    ): JsResult = withContext(Dispatchers.Default) {
        val counter = AtomicLong(0L)
        val factory = object : ContextFactory() {
            override fun observeInstructionCount(cx: Context, instructionCount: Int) {
                if (counter.addAndGet(instructionCount.toLong()) > instructionLimit) {
                    throw RhinoTimeoutException()
                }
            }
        }
        val cx = factory.enterContext()
        try {
            // 每 10_000 条指令回调一次 observeInstructionCount
            cx.instructionObserverThreshold = 10_000
            val scope: Scriptable = cx.initStandardObjects()
            for ((k, v) in bindings) scope.put(k, scope, v)
            val out = cx.evaluateString(scope, script, "shell-eval", 1, null)
            JsResult.Success(out.toString())
        } catch (e: RhinoTimeoutException) {
            JsResult.Timeout
        } catch (e: Exception) {
            JsResult.Failure(e)
        } finally {
            Context.exit()
        }
    }
}

package com.aggregator.shell.core.source.sandbox

import com.aggregator.shell.core.common.AppException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.util.concurrent.atomic.AtomicReference

/**
 * Rhino-backed JS sandbox. Rhino is pure JVM (no NDK), so the shell builds
 * without native toolchains. Timeout is enforced by running the evaluation
 * on a dedicated thread and cancelling it after [timeoutMillis].
 */
class RhinoJsExecutor : JsSandboxExecutor {

    override suspend fun execute(
        script: String,
        bindings: Map<String, Any>,
        timeoutMillis: Long
    ): JsResult = withContext(Dispatchers.IO) {
        val holder = AtomicReference<JsResult>()
        val thread = Thread {
            val cx = Context.enter()
            try {
                val scope: Scriptable = cx.initStandardObjects()
                for ((k, v) in bindings) scope.put(k, scope, v)
                val out = cx.evaluateString(scope, script, "shell-eval", 1, null)
                holder.set(JsResult.Success(out.toString()))
            } catch (e: Exception) {
                holder.set(JsResult.Failure(e))
            } finally {
                Context.exit()
            }
        }
        thread.start()
        thread.join(timeoutMillis)
        if (thread.isAlive) {
            thread.interrupt()
            JsResult.Timeout
        } else holder.get()
            ?: JsResult.Failure(RuntimeException("no result"))
    }
}

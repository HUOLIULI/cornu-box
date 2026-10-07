package com.aggregator.shell.core.source.sandbox

import com.aggregator.shell.core.common.AppException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Python runtime for TVBox py / py.json mixed sources.
 *
 * The default build ships [NoOpPythonRuntime] so the shell is buildable
 * without CPython / Chaquopy. To enable real Python execution, add the
 * `com.chaquo.python` plugin to `:core:source` and bind a Chaquopy-backed
 * implementation of this interface instead (see docs, "Build Feasibility").
 */
interface PythonRuntime {
    val isAvailable: Boolean
    suspend fun invoke(spiderScript: String, method: String, vararg args: Any): String
}

class NoOpPythonRuntime : PythonRuntime {
    override val isAvailable: Boolean = false

    override suspend fun invoke(
        spiderScript: String,
        method: String,
        vararg args: Any
    ): String = withContext(Dispatchers.IO) {
        throw AppException.SourceInvalidException(
            "Python 源 $spiderScript 不可用（默认构建未启用 Chaquopy）"
        )
    }
}

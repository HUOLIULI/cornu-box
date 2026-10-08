package com.aggregator.shell

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class MediaShellApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Log any uncaught exception to a file so a first-run crash is inspectable.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            try {
                val dir = filesDir
                val f = File(dir, "crash-$stamp.log")
                f.writeText(
                    "thread=${thread.name}\n" +
                            throwable.stackTraceToString() +
                            "\ncontext=$this\n"
                )
            } catch (e: Exception) {
                Log.e("MediaShell", "crash-log failed", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

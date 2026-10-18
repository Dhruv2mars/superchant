package com.superchant

import android.os.StrictMode

/** Debug builds log StrictMode violations (disk I/O on the main thread, leaked closeables, ...). */
object DebugStrictMode {
    private var enabled = false

    fun enable() {
        if (!BuildConfig.DEBUG || enabled) return
        enabled = true
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
    }
}

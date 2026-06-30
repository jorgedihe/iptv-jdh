package com.m3u.data.codec

import android.content.Context

/**
 * NOTE: the previous external library loader was removed for Google Play
 * policy compliance. We now only load NDK libraries bundled inside the APK
 * via the standard System.loadLibrary path.
 */
object CodecNativeLoader {
    private val loadedLibraries = mutableSetOf<String>()

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun initialize(context: Context) {
        // No-op. Kept so existing call sites compile.
    }

    @JvmStatic
    fun loadLibrary(name: String) {
        synchronized(loadedLibraries) {
            if (name in loadedLibraries) return
        }
        System.loadLibrary(name)
        synchronized(loadedLibraries) { loadedLibraries += name }
    }
}

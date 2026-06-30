package com.m3u.data.codec

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * NOTE: the previous runtime codec-pack download/install path was removed
 * for Google Play policy compliance. The repository now reports the feature
 * as permanently disabled / not-installed, the UI surfaces are hidden, and
 * playback relies on the codecs bundled inside the APK only. The class is
 * kept as a no-op singleton so the DI graph stays unchanged.
 */
@Singleton
class CodecPackRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val applicationContext = context.applicationContext
    private val timber = Timber.tag("CodecPackRepository")

    /** Always false — the runtime download/install path has been removed. */
    val enabled: Boolean = false

    val packId: String = "disabled"

    val currentAbi: String? get() = Build.SUPPORTED_ABIS.firstOrNull()

    fun isInstalled(): Boolean = false

    fun deleteInstalledPack() {
        // Clean up any leftovers from earlier installs so the app's
        // noBackupFilesDir doesn't keep dead bytes around.
        runCatching {
            File(applicationContext.noBackupFilesDir, CodecPackConfig.DIRECTORY)
                .deleteRecursively()
        }.onFailure { timber.w(it, "deleteInstalledPack cleanup failed") }
    }

    fun readInstalledManifest(): CodecPackManifest? = null

    fun installFromDefaultSnapshot(): CodecPackInstallResult =
        CodecPackInstallResult.Disabled

    @Suppress("UNUSED_PARAMETER")
    fun installFromManifestUrl(manifestUrl: String): CodecPackInstallResult =
        CodecPackInstallResult.Disabled
}

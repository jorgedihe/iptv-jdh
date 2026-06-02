package com.m3u.smartphone

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.m3u.core.extension.Utils
import com.m3u.data.codec.CodecPackRepository
import com.m3u.i18n.R.string
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.acra.config.mailSender
import org.acra.config.notification
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

@HiltAndroidApp
class M3UApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var codecPackRepository: CodecPackRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
        Utils.init(this)

        // Auto-install the FFmpeg codec pack on first launch so users don't
        // have to discover a setting in order to watch HEVC / AC-3 / E-AC-3
        // streams. Use android.util.Log directly (not Timber) so R8 doesn't
        // strip the messages from release builds.
        val tag = "IPTV-JDH-Codec"
        android.util.Log.i(
            tag,
            "Application onCreate: enabled=${codecPackRepository.enabled} " +
                    "installed=${codecPackRepository.isInstalled()} " +
                    "abi=${codecPackRepository.currentAbi}"
        )
        if (codecPackRepository.enabled && !codecPackRepository.isInstalled()) {
            applicationScope.launch {
                android.util.Log.i(tag, "Starting codec pack download…")
                runCatching {
                    codecPackRepository.installFromDefaultSnapshot()
                }.fold(
                    onSuccess = { result ->
                        android.util.Log.i(tag, "Codec pack install result: $result")
                    },
                    onFailure = { t ->
                        android.util.Log.e(tag, "Codec pack install FAILED", t)
                    }
                )
            }
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            notification {
                title = getString(string.crash_notification_title)
                text = getString(string.crash_notification_text)
                channelName = getString(string.crash_notification_channel_name)
            }
            mailSender {
                mailTo = "oxyroid@outlook.com"
                reportAsFile = true
                reportFileName = "Crash.txt"
            }
        }
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}

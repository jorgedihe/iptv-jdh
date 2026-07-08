package com.m3u.smartphone

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.m3u.core.extension.Utils
import com.m3u.i18n.R.string
import dagger.hilt.android.HiltAndroidApp
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

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
        Utils.init(this)
        // NOTE: previous versions auto-downloaded an FFmpeg codec pack here
        // from a remote URL at first launch. That was removed to comply with
        // Google Play's "Device and Network Abuse" policy. We now rely on the
        // codecs bundled inside the APK only.
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
                // Was "oxyroid@outlook.com" (upstream M3UAndroid author).
                // Redirected to the IPTV JDH maintainer so crash reports
                // actually reach someone who can act on them.
                mailTo = "contacto@jorgedihe.net"
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

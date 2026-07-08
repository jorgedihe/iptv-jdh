package com.m3u.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.data.R
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.i18n.R.string
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicInteger

@HiltWorker
class SubscriptionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
    private val programmeRepository: ProgrammeRepository,
    private val notificationManager: NotificationManager,
    private val workManager: WorkManager,
) : CoroutineWorker(context, params) {
    private val dataSource = inputData
        .getString(INPUT_STRING_DATA_SOURCE_VALUE)
        ?.let { DataSource.ofOrNull(it) }

    private val title = inputData.getString(INPUT_STRING_TITLE)
    private val basicUrl = inputData.getString(INPUT_STRING_BASIC_URL)
    private val username = inputData.getString(INPUT_STRING_USERNAME)
    private val password = inputData.getString(INPUT_STRING_PASSWORD)
    private val url = inputData.getString(INPUT_STRING_URL)
    private val epgPlaylistUrl = inputData.getString(INPUT_STRING_EPG_PLAYLIST_URL)
    private val epgIgnoreCache = inputData.getBoolean(INPUT_BOOLEAN_EPG_IGNORE_CACHE, false)
    private val notificationId: Int by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ATOMIC_NOTIFICATION_ID.incrementAndGet()
    }

    override suspend fun doWork(): Result = coroutineScope {
        dataSource ?: return@coroutineScope Result.failure()
        createChannel()
        coroutineContext[Job]?.invokeOnCompletion { cause ->
            when {
                cause == null -> {}
                cause is CancellationException -> {
                    notificationManager.cancel(notificationId)
                }

                // Provider-side transient failure (server returned an HTML
                // error page instead of JSON, or the network dropped): don't
                // spam the notification shade with a red banner every time.
                // The user already has cached channels, so silently
                // dismiss and let the next natural refresh recover.
                isProviderIssue(cause) -> {
                    notificationManager.cancel(notificationId)
                }

                else -> {
                    createN10nBuilder()
                        .setContentText(friendlyErrorMessage(cause))
                        .setActions(retryAction)
                        .setColor(Color.RED)
                        .buildThenNotify()
                }
            }
        }
        when (dataSource) {
            DataSource.M3U -> {
                val title = title ?: return@coroutineScope Result.failure()
                val url = url ?: return@coroutineScope Result.failure()
                if (title.isEmpty()) {
                    val message = context.getString(string.data_error_empty_title)
                    createN10nBuilder()
                        .setContentText(message)
                        .buildThenNotify()
                    Result.failure()
                } else {
                    var total = 0
                    playlistRepository.m3uOrThrow(title, url) { count ->
                        total = count
                        val notification = createN10nBuilder()
                            .setContentText(findChannelProgressContentText(count))
                            .setActions(cancelAction)
                            .setOngoing(true)
                            .build()
                        notificationManager.notify(notificationId, notification)
                    }

                    createN10nBuilder()
                        .setContentText(findCompleteContentText(total))
                        .buildThenNotify()
                    Result.success()
                }
            }

            DataSource.EPG -> {
                val playlistUrl = epgPlaylistUrl ?: return@coroutineScope Result.failure()
                val ignoreCache = epgIgnoreCache
                try {
                    // BUG FIX: this used to do `.launchIn(this)` and return
                    // Result.success() immediately. WorkManager then tore the
                    // worker down (4 ms from Start to SUCCESS in logcat),
                    // cancelling the orphaned coroutine — so the XMLTV download
                    // never actually completed. Switching to .collect{} makes
                    // the worker await the entire flow, which is exactly what
                    // the foreground service is for in the first place.
                    programmeRepository.checkOrRefreshProgrammesOrThrow(
                        playlistUrl,
                        ignoreCache = ignoreCache
                    )
                        .collect { count ->
                            val notification = createN10nBuilder()
                                .setContentText(findProgrammeProgressContentText(count))
                                .setActions(cancelAction)
                                .build()
                            notificationManager.notify(notificationId, notification)
                        }
                    Result.success()
                } catch (e: Exception) {
                    createN10nBuilder()
                        .setContentText(friendlyErrorMessage(e))
                        .setActions(retryAction)
                        .setColor(Color.RED)
                        .buildThenNotify()
                    e.printStackTrace()
                    Result.failure()
                }
            }

            DataSource.Xtream -> {
                title ?: return@coroutineScope Result.failure()
                basicUrl ?: return@coroutineScope Result.failure()
                username ?: return@coroutineScope Result.failure()
                password ?: return@coroutineScope Result.failure()
                if (title.isEmpty()) {
                    url ?: return@coroutineScope Result.failure()
                    val message = context.getString(string.data_error_empty_title)
                    createN10nBuilder()
                        .setContentText(message)
                        .buildThenNotify()
                    Result.failure()
                } else {
                    try {
                        val type = url?.let { XtreamInput.decodeFromPlaylistUrlOrNull(it)?.type }
                        var total = 0
                        playlistRepository.xtreamOrThrow(
                            title, basicUrl, username, password, type
                        ) { count ->
                            total = count
                            val notification = createN10nBuilder()
                                .setContentText(findChannelProgressContentText(count))
                                .setActions(cancelAction)
                                .build()
                            notificationManager.notify(notificationId, notification)
                        }
                        createN10nBuilder()
                            .setContentText(findCompleteContentText(total))
                            .buildThenNotify()
                        Result.success()
                    } catch (e: Exception) {
                        createN10nBuilder()
                            .setContentText(friendlyErrorMessage(e))
                            .setActions(retryAction)
                            .setColor(Color.RED)
                            .buildThenNotify()
                        Result.failure()
                    }
                }
            }

            else -> {
                // do nothing
                Result.failure()
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, NOTIFICATION_NAME, NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "display subscribe task progress"
        notificationManager.createNotificationChannel(channel)
    }

    private fun Notification.Builder.buildThenNotify() {
        if (isStopped) return
        notificationManager.notify(notificationId, build())
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(notificationId, createN10nBuilder().build())
    }

    private fun createN10nBuilder(): Notification.Builder =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_file_download_24)
            // Sanitised title: never leak the raw URL (which for Xtream carries
            // the user's credentials in the query string) into the OS notification
            // shade or Samsung's notification history.
            .setContentTitle(
                when (dataSource) {
                    DataSource.EPG -> sanitizedTitle(epgPlaylistUrl)
                    else -> sanitizedTitle(title)
                }
            )

    /**
     * Translate the many low-level failure modes of the Xtream / M3U / EPG
     * pipeline into a single short human sentence. The original code dumped
     * `throwable.localizedMessage` straight into the notification, which
     * exposed XML parser stacktraces like
     *   "expected: /hr read: body (position:END_TAG </body ...)"
     * to non-technical users.
     */
    private fun friendlyErrorMessage(cause: Throwable): String {
        val raw = cause.message.orEmpty()
        val className = cause::class.java.simpleName
        return when {
            // The Xtream endpoint returned an HTML error page instead of JSON —
            // most common cause of the "expected: /hr" notification.
            raw.contains("hr read", ignoreCase = true) ||
                    raw.contains("END_TAG", ignoreCase = true) ||
                    raw.contains("<html", ignoreCase = true) ||
                    raw.contains("<body", ignoreCase = true) ->
                "El proveedor no responde correctamente. Vuelve a intentarlo más tarde."

            // Serialization / JSON failures — server returned malformed body.
            className.contains("Serialization", ignoreCase = true) ||
                    raw.contains("Unexpected JSON", ignoreCase = true) ||
                    raw.contains("JsonDecodingException", ignoreCase = true) ->
                "Respuesta del servidor no válida. Reintenta más tarde."

            // Network layer.
            className.contains("UnknownHost", ignoreCase = true) ||
                    raw.contains("Unable to resolve host", ignoreCase = true) ->
                "Sin conexión con el proveedor. Comprueba tu red."

            className.contains("SocketTimeout", ignoreCase = true) ||
                    raw.contains("timeout", ignoreCase = true) ->
                "El proveedor tarda demasiado en responder."

            className.contains("SSL", ignoreCase = true) ||
                    className.contains("Certificate", ignoreCase = true) ->
                "Problema de conexión segura con el proveedor."

            // HTTP status codes typically leak through as "code=458" etc.
            raw.contains("code=4", ignoreCase = false) ||
                    raw.contains("code=5", ignoreCase = false) ->
                "El proveedor devolvió un error. Reintenta más tarde."

            else ->
                "No se pudo actualizar la lista. Vuelve a intentarlo."
        }
    }

    /**
     * True when the failure was clearly on the provider's side (transient
     * HTML error page, malformed JSON, timeout, HTTP 4xx/5xx). We use this
     * to silently swallow those errors instead of spamming a red banner
     * every time the Xtream endpoint hiccups.
     */
    private fun isProviderIssue(cause: Throwable): Boolean {
        val raw = cause.message.orEmpty()
        val className = cause::class.java.simpleName
        return raw.contains("hr read", ignoreCase = true) ||
                raw.contains("END_TAG", ignoreCase = true) ||
                raw.contains("<html", ignoreCase = true) ||
                raw.contains("<body", ignoreCase = true) ||
                className.contains("Serialization", ignoreCase = true) ||
                raw.contains("Unexpected JSON", ignoreCase = true) ||
                raw.contains("JsonDecodingException", ignoreCase = true) ||
                className.contains("SocketTimeout", ignoreCase = true) ||
                raw.contains("timeout", ignoreCase = true) ||
                raw.contains("code=4", ignoreCase = false) ||
                raw.contains("code=5", ignoreCase = false)
    }

    /**
     * Strip credentials from anything that might be a URL. If the string looks
     * like a normal playlist title we return it unchanged.
     */
    private fun sanitizedTitle(raw: String?): String {
        val fallback = "IPTV JDH"
        val value = raw?.takeIf { it.isNotBlank() } ?: return fallback
        // Detect obvious URL-shaped inputs.
        if (!value.contains("://") && !value.contains("player_api.php")) return value
        return runCatching {
            val uri = android.net.Uri.parse(value)
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return@runCatching fallback
            // e.g. "IPTV JDH · es.iptvharmony.co"
            "IPTV JDH · $host"
        }.getOrDefault(fallback)
    }

    private fun findCancelActionTitle() =
        context.getString(string.data_worker_subscription_action_cancel)

    private fun findRetryActionTitle() =
        context.getString(string.data_worker_subscription_action_retry)

    private fun findCompleteContentText(total: Int) =
        context.getString(string.data_worker_subscription_content_completed, total)

    private fun findChannelProgressContentText(count: Int) =
        context.getString(string.data_worker_subscription_content_channel_progress, count)

    private fun findProgrammeProgressContentText(count: Int) =
        context.getString(string.data_worker_subscription_content_programme_progress, count)

    private val cancelAction: Notification.Action by lazy {
        Notification.Action.Builder(
            Icon.createWithResource(
                context,
                R.drawable.round_cancel_24
            ),
            findCancelActionTitle(),
            workManager.createCancelPendingIntent(id)
        )
            .build()
    }
    private val retryAction: Notification.Action by lazy {
        Notification.Action.Builder(
            Icon.createWithResource(
                context,
                R.drawable.round_refresh_24
            ),
            findRetryActionTitle(),
            PendingIntent.getForegroundService(
                context,
                1234,
                Intent(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "subscribe_channel"
        private const val NOTIFICATION_NAME = "subscribe task"
        private const val INPUT_STRING_TITLE = "title"
        private const val INPUT_STRING_URL = "url"
        private const val INPUT_STRING_EPG_PLAYLIST_URL = "epg"
        private const val INPUT_BOOLEAN_EPG_IGNORE_CACHE = "ignore_cache"
        private const val INPUT_STRING_BASIC_URL = "basic_url"
        private const val INPUT_STRING_USERNAME = "username"
        private const val INPUT_STRING_PASSWORD = "password"
        private const val INPUT_STRING_DATA_SOURCE_VALUE = "data-source"
        const val TAG = "subscription"

        fun m3u(
            workManager: WorkManager,
            title: String,
            url: String
        ) {
            // Use a unique-work namespace per type ("m3u#…", "epg#…", "xtream#…")
            // so refreshes don't kill siblings that share the same URL tag.
            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_STRING_TITLE to title,
                        INPUT_STRING_URL to url,
                        INPUT_STRING_DATA_SOURCE_VALUE to DataSource.M3U.value
                    )
                )
                .addTag(url)
                .addTag(TAG)
                .addTag(DataSource.M3U.value)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork("m3u#$url", ExistingWorkPolicy.REPLACE, request)
        }

        fun epg(
            workManager: WorkManager,
            playlistUrl: String,
            ignoreCache: Boolean
        ) {
            // Don't cancelAllWorkByTag(playlistUrl): that tag is shared with the
            // channel-download worker (xtream/m3u), and cancelling it kills the
            // sibling that is still pulling Live/VOD/Series. Use a dedicated
            // unique work name for EPG instead.
            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_STRING_EPG_PLAYLIST_URL to playlistUrl,
                        INPUT_BOOLEAN_EPG_IGNORE_CACHE to ignoreCache,
                        INPUT_STRING_DATA_SOURCE_VALUE to DataSource.EPG.value,
                    )
                )
                .addTag(playlistUrl)
                .addTag(TAG)
                .addTag(DataSource.EPG.value)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(
                "epg#$playlistUrl",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun xtream(
            workManager: WorkManager,
            title: String,
            url: String,
            basicUrl: String,
            username: String,
            password: String,
        ) {
            // Unique-work namespace "xtream#<url>" ensures:
            // - Refreshing this exact playlist replaces a previous run (REPLACE).
            // - Sibling playlists (Live/VOD/Series of the same provider) have
            //   different URLs → different unique names → they don't kill each other.
            // - The EPG worker uses "epg#…", so it can't kill the channel worker.
            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_STRING_TITLE to title,
                        INPUT_STRING_URL to url,
                        INPUT_STRING_BASIC_URL to basicUrl,
                        INPUT_STRING_USERNAME to username,
                        INPUT_STRING_PASSWORD to password,
                        INPUT_STRING_DATA_SOURCE_VALUE to DataSource.Xtream.value
                    )
                )
                .addTag(url)
                .addTag(basicUrl)
                .addTag(DataSource.Xtream.value)
                .apply {
                    val xtreamInput = XtreamInput.decodeFromPlaylistUrlOrNull(url) ?: XtreamInput(
                        basicUrl = basicUrl,
                        username = username,
                        password = password
                    )
                    val type = xtreamInput.type
                    if (type == null) {
                        addTag(
                            XtreamInput.encodeToPlaylistUrl(
                                xtreamInput.copy(
                                    type = DataSource.Xtream.TYPE_LIVE
                                )
                            )
                        )
                        addTag(
                            XtreamInput.encodeToPlaylistUrl(
                                xtreamInput.copy(
                                    type = DataSource.Xtream.TYPE_SERIES
                                )
                            )
                        )
                        addTag(
                            XtreamInput.encodeToPlaylistUrl(
                                xtreamInput.copy(
                                    type = DataSource.Xtream.TYPE_VOD
                                )
                            )
                        )
                    } else {
                        addTag(
                            XtreamInput.encodeToPlaylistUrl(
                                xtreamInput.copy(
                                    type = type
                                )
                            )
                        )
                    }
                }
                .addTag(TAG)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(
                "xtream#$url",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private val ATOMIC_NOTIFICATION_ID = AtomicInteger()
    }
}

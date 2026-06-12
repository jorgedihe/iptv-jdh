package com.m3u.business.channel

import com.m3u.data.database.model.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.mm2d.upnp.ControlPoint
import net.mm2d.upnp.ControlPointFactory
import net.mm2d.upnp.Device

internal class DlnaController : ControlPoint.DiscoveryListener {
    private val _devices = MutableStateFlow(emptyList<Device>())
    val devices = _devices.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()

    private val _connectedDeviceUdn = MutableStateFlow<String?>(null)
    val connectedDeviceUdn = _connectedDeviceUdn.asStateFlow()

    private var controlPoint: ControlPoint? = null

    fun startSearch() {
        stopSearch(clearDevices = false)
        _searching.value = true
        runCatching {
            controlPoint = ControlPointFactory.create().apply {
                addDiscoveryListener(this@DlnaController)
                initialize()
                start()
                search(SEARCH_TARGET_MEDIA_RENDERER)
            }
        }.onFailure {
            _searching.value = false
        }
    }

    fun stopSearch(clearDevices: Boolean = true) {
        runCatching {
            _searching.value = false
            controlPoint?.removeDiscoveryListener(this)
            controlPoint?.stop()
            controlPoint?.terminate()
            controlPoint = null
            if (clearDevices) {
                _devices.value = emptyList()
            }
        }
    }

    override fun onDiscover(device: Device) {
        if (!device.isPlayableRenderer()) return
        _devices.update { devices ->
            if (devices.any { it.udn == device.udn }) devices else devices + device
        }
    }

    override fun onLost(device: Device) {
        _devices.update { devices ->
            devices.filterNot { it.udn == device.udn }
        }
    }

    fun play(device: Device, channel: Channel) {
        val setUri = device.findAction(ACTION_SET_AV_TRANSPORT_URI) ?: run {
            android.util.Log.w(TAG, "play: SetAVTransportURI action not found on " + device.friendlyName)
            return
        }
        android.util.Log.i(TAG, "play -> " + device.friendlyName + " url=" + channel.url)
        setUri.invoke(
            argumentValues = mapOf(
                INSTANCE_ID to DEFAULT_INSTANCE_ID,
                CURRENT_URI to channel.url,
                CURRENT_URI_META_DATA to channel.toDidlLite()
            ),
            onResult = { result ->
                if (result.containsKey("errorCode") || result.containsKey("UPnPError")) {
                    android.util.Log.w(TAG, "SetAVTransportURI failed on " + device.friendlyName + ": " + result)
                    return@invoke
                }
                device.findAction(ACTION_PLAY)?.invoke(
                    argumentValues = mapOf(
                        INSTANCE_ID to DEFAULT_INSTANCE_ID,
                        SPEED to DEFAULT_SPEED
                    ),
                    onResult = { playResult ->
                        if (playResult.containsKey("errorCode") || playResult.containsKey("UPnPError")) {
                            android.util.Log.w(TAG, "Play action failed on " + device.friendlyName + ": " + playResult)
                            return@invoke
                        }
                        _connectedDeviceUdn.value = device.udn
                    }
                )
            }
        )
    }

    fun stop(device: Device) {
        device.findAction(ACTION_STOP)?.invoke(
            argumentValues = mapOf(
                INSTANCE_ID to DEFAULT_INSTANCE_ID
            ),
            onResult = {
                if (_connectedDeviceUdn.value == device.udn) {
                    _connectedDeviceUdn.value = null
                }
            }
        )
    }

    private fun Device.isPlayableRenderer(): Boolean {
        return findAction(ACTION_SET_AV_TRANSPORT_URI) != null &&
                findAction(ACTION_PLAY) != null
    }

    private fun Channel.toDidlLite(): String {
        val title = title.ifBlank { url }
        val creator = category.ifBlank { "IPTV JDH" }
        val mime = inferMimeType(url)
        val protocolInfo = "http-get:*:" + mime + ":" + DLNA_FLAGS
        return buildString {
            append("<DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
            append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" ")
            append("xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\">")
            append("<item id=\"0\" parentID=\"0\" restricted=\"1\">")
            append("<dc:title>")
            append(title.escapeXml())
            append("</dc:title>")
            append("<dc:creator>")
            append(creator.escapeXml())
            append("</dc:creator>")
            append("<upnp:class>object.item.videoItem</upnp:class>")
            cover?.takeIf { it.isNotBlank() }?.let {
                append("<upnp:albumArtURI>")
                append(it.escapeXml())
                append("</upnp:albumArtURI>")
            }
            append("<res protocolInfo=\"")
            append(protocolInfo.escapeXml())
            append("\">")
            append(url.escapeXml())
            append("</res>")
            append("</item>")
            append("</DIDL-Lite>")
        }
    }

    private fun inferMimeType(url: String): String {
        val cleanPath = url.substringBefore('?').substringBefore('#')
        val ext = cleanPath.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "m3u8" -> "application/vnd.apple.mpegurl"
            "mpd" -> "application/dash+xml"
            "ts", "m2ts" -> "video/mp2t"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "3gp" -> "video/3gpp"
            else -> "video/mpeg"
        }
    }

    private fun String.escapeXml(): String {
        return buildString(length) {
            this@escapeXml.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(char)
                }
            }
        }
    }

    companion object {
        private const val TAG = "DlnaController"
        private const val SEARCH_TARGET_MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

        private const val ACTION_SET_AV_TRANSPORT_URI = "SetAVTransportURI"
        private const val ACTION_PLAY = "Play"
        private const val ACTION_STOP = "Stop"

        private const val INSTANCE_ID = "InstanceID"
        private const val DEFAULT_INSTANCE_ID = "0"
        private const val CURRENT_URI = "CurrentURI"
        private const val CURRENT_URI_META_DATA = "CurrentURIMetaData"
        private const val SPEED = "Speed"
        private const val DEFAULT_SPEED = "1"

        private const val DLNA_FLAGS =
            "DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
    }
}

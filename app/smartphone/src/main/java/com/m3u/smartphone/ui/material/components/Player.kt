package com.m3u.smartphone.ui.material.components

import android.app.Activity
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.m3u.core.foundation.architecture.preferences.ClipMode

@Immutable
data class PlayerState(
    val player: Player?,
    @ClipMode val clipMode: Int,
    val keepScreenOn: Boolean
) {
    var surface: Surface? by mutableStateOf(null)
}

@Composable
fun rememberPlayerState(
    player: Player?,
    @ClipMode clipMode: Int = ClipMode.ADAPTIVE,
    keepScreenOn: Boolean = true,
): PlayerState {
    return remember(
        player,
        clipMode,
        keepScreenOn
    ) {
        PlayerState(
            player,
            clipMode,
            keepScreenOn
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun Player(
    state: PlayerState,
    modifier: Modifier = Modifier
) {
    val player = state.player
    val keepScreenOn = state.keepScreenOn
    val clipMode = state.clipMode

    // Add FLAG_KEEP_SCREEN_ON to the Activity window while the player is on
    // screen. Setting the flag on PlayerView alone is not always honored when
    // the view is recycled or re-attached, so we hold it at window level.
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        val activity = view.context as? Activity
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                videoSurfaceView
            }
        },
        update = { view ->
            view.apply {
                this.player = player
                setKeepScreenOn(keepScreenOn)
                resizeMode = when (clipMode) {
                    ClipMode.ADAPTIVE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    ClipMode.CLIP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    ClipMode.STRETCHED -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        },
        onRelease = {
            it.player = null
            it.keepScreenOn = false
        }
    )
}

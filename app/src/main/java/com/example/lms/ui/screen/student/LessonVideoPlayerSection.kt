package com.example.lms.ui.screen.student

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun LessonVideoSection(
    videoUrl: String,
    exoPlayer: ExoPlayer,
    isFullscreen: Boolean,
    isPortrait: Boolean,
    playbackPositionMs: Long,
    playbackWhenReady: Boolean,
    onPlaybackSnapshot: (Long, Boolean) -> Unit,
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    fun snapshotPlaybackState() {
        onPlaybackSnapshot(exoPlayer.currentPosition.coerceAtLeast(0L), exoPlayer.playWhenReady)
    }

    val shouldShowFullscreen = isFullscreen || !isPortrait

    if (shouldShowFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VideoPlayer(
                videoUrl = videoUrl,
                exoPlayer = exoPlayer,
                isFullscreen = true,
                initialSeekPositionMs = playbackPositionMs,
                resumeWhenReady = playbackWhenReady,
                onPlaybackSnapshot = onPlaybackSnapshot,
                onToggleFullscreen = {
                    snapshotPlaybackState()
                    onExitFullscreen()
                }
            )
        }
    } else {
        VideoPlayer(
            videoUrl = videoUrl,
            exoPlayer = exoPlayer,
            isFullscreen = false,
            initialSeekPositionMs = playbackPositionMs,
            resumeWhenReady = playbackWhenReady,
            onPlaybackSnapshot = onPlaybackSnapshot,
            onToggleFullscreen = {
                snapshotPlaybackState()
                onEnterFullscreen()
            }
        )
    }
}

@Composable
fun VideoPlayer(
    videoUrl: String,
    exoPlayer: ExoPlayer,
    isFullscreen: Boolean,
    initialSeekPositionMs: Long,
    resumeWhenReady: Boolean,
    onPlaybackSnapshot: (Long, Boolean) -> Unit,
    onToggleFullscreen: () -> Unit
) {
    val context = LocalContext.current
    var restoredForUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoUrl, exoPlayer) {
        val targetUrl = videoUrl.trim()
        if (targetUrl.isBlank()) return@LaunchedEffect

        val currentUrl = exoPlayer.currentMediaItem
            ?.localConfiguration
            ?.uri
            ?.toString()
            .orEmpty()
            .trim()

        if (currentUrl != targetUrl) {
            val wasPlaying = exoPlayer.playWhenReady
            exoPlayer.setMediaItem(MediaItem.fromUri(targetUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = wasPlaying
            restoredForUrl = null
        }

        if (restoredForUrl != targetUrl) {
            if (initialSeekPositionMs > 0L) {
                exoPlayer.seekTo(initialSeekPositionMs)
            }
            exoPlayer.playWhenReady = resumeWhenReady
            restoredForUrl = targetUrl
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            onPlaybackSnapshot(exoPlayer.currentPosition.coerceAtLeast(0L), exoPlayer.playWhenReady)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFullscreen) Modifier.fillMaxHeight()
                else Modifier.aspectRatio(16f / 9f)
            )
            .background(Color.Black)
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                it.player = null
            }
        )

        IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(36.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(
                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (isFullscreen) "Thoát toàn màn hình" else "Toàn màn hình",
                tint = Color.White
            )
        }
    }
}

@Composable
fun LessonPlayerFullscreenEffect(isFullscreen: Boolean) {
    val view = LocalView.current

    LaunchedEffect(isFullscreen, view) {
        val activity = view.context as? Activity
        val window = activity?.window
        if (activity != null && window != null) {
            val controller = WindowInsetsControllerCompat(window, view)

            if (isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(view) {
        val activity = view.context as? Activity
        val window = activity?.window
        onDispose {
            if (activity != null && window != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}



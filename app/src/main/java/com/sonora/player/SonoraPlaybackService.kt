package com.sonora.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Media playback host that survives UI navigation and exposes system media controls.
 */
@OptIn(UnstableApi::class)
class SonoraPlaybackService : MediaSessionService() {
    companion object {
        @Volatile var audioSessionId: Int = 0
            private set
    }
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                    true
                )
            }
        player = exo
        audioSessionId = exo.audioSessionId
        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Companion.audioSessionId = audioSessionId
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("SONORA_PLAYBACK", "Media3 service playback error: ${error.errorCodeName}", error)
            }
        })
        mediaSession = MediaSession.Builder(this, exo).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        audioSessionId = 0
        super.onDestroy()
    }
}

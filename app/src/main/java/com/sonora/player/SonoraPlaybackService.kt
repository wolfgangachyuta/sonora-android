package com.sonora.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Media playback host that can outlive the UI.
 * This milestone establishes the service/session foundation; the UI still uses
 * its current in-activity player until controller wiring is completed.
 */
class SonoraPlaybackService : MediaSessionService() {
    companion object {
        @Volatile var audioSessionId: Int = 0
            private set
    }
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        audioSessionId = exo.audioSessionId
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

package com.kpop.gummy

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

data class RadioStation(
    val id: String,
    val name: String,
    val url: String
)

val stations = listOf(
    RadioStation(
        id = "listen-moe-kpop",
        name = "LISTEN.moe K-POP",
        url = "https://listen.moe/kpop/stream"
    ),
    RadioStation(
        id = "big-b-kpop",
        name = "Big B Radio KPOP",
        url = "https://antares.dribbcast.com/proxy/kpop?mp=/s"
    )
)

class RadioService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                volume = 0.7f
                repeatMode = Player.REPEAT_MODE_ALL

                setMediaItems(
                    stations.map { station ->
                        MediaItem.Builder()
                            .setMediaId(station.id)
                            .setUri(station.url)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(station.name)
                                    .setArtist("K-Pop Gummy Radio")
                                    .build()
                            )
                            .build()
                    }
                )
            }

        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        player.release()
        super.onDestroy()
    }
}

package com.velvet.music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.velvet.music.MainActivity
import com.velvet.music.R

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var spectrum: SpectrumCapture? = null

    companion object {
        @Volatile
        private var instance: PlaybackService? = null

        /** Called after RECORD_AUDIO is granted so capture can attach mid-playback. */
        fun refreshSpectrumCapture() {
            val service = instance ?: return
            val player = service.exoPlayer ?: return
            if (player.isPlaying) service.spectrum?.attach(player.audioSessionId)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        exoPlayer = player

        val channelId = "velvet_now_playing"
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
        setMediaNotificationProvider(VelvetNotificationProvider(this, channelId))

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val capture = SpectrumCapture(this)
        spectrum = capture
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (player.playWhenReady) capture.attach(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) capture.attach(player.audioSessionId)
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        spectrum?.release()
        spectrum = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        if (instance === this) instance = null
        super.onDestroy()
    }
}

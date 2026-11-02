package com.velvet.music.volume

import android.media.AudioManager

const val ACTION_APP_VOLUME = "com.velvet.music.action.APP_VOLUME"
const val EXTRA_VOLUME = "com.velvet.music.extra.VOLUME"

internal data class VelvetStream(
    val stream: Int,
    val label: String,
    val detail: String
)

internal val velvetStreams = listOf(
    VelvetStream(AudioManager.STREAM_MUSIC, "Media", "Music and podcasts"),
    VelvetStream(AudioManager.STREAM_RING, "Ring", "Incoming calls"),
    VelvetStream(AudioManager.STREAM_NOTIFICATION, "Alerts", "Notifications"),
    VelvetStream(AudioManager.STREAM_ALARM, "Alarm", "Clock and timers"),
    VelvetStream(AudioManager.STREAM_VOICE_CALL, "Calls", "Call audio")
)

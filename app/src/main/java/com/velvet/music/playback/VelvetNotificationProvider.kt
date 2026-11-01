package com.velvet.music.playback

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import android.support.v4.media.session.PlaybackStateCompat
import com.google.common.collect.ImmutableList
import com.velvet.music.R
import com.velvet.music.data.Prefs
import com.velvet.music.ui.theme.paletteFor
import java.util.concurrent.TimeUnit

/**
 * Media-style notification with a framed, glowing artwork and live transport actions.
 * The frame is rendered once per update into a notification large icon.
 */
class VelvetNotificationProvider(
    private val context: Context,
    private val channelId: String
) : MediaNotification.Provider {

    override fun createNotification(
        session: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val player = session.player
        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title?.toString().orEmpty().ifBlank { context.getString(R.string.app_name) }
        val artist = metadata?.artist?.toString().orEmpty()
        val album = metadata?.albumTitle?.toString().orEmpty()
        val accent = paletteFor(Prefs(context).accent).primary.toArgb()
        val playing = player.isPlaying

        val largeIcon = runCatching {
            ArtworkFrame.render(session, metadata?.artworkUri, accent, playing)
        }.getOrNull()

        val style = MediaStyleNotificationHelper.MediaStyle(session)
            .setShowActionsInCompactView(0, 1, 2)

        val previousIntent = actionFactory.createMediaActionPendingIntent(
            session,
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS.toLong()
        )
        val playPauseIntent = actionFactory.createMediaActionPendingIntent(
            session,
            PlaybackStateCompat.ACTION_PLAY_PAUSE.toLong()
        )
        val nextIntent = actionFactory.createMediaActionPendingIntent(
            session,
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT.toLong()
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_velvet)
            .setContentTitle(title)
            .setContentText(artist.ifBlank { album.ifBlank { "Velvet" } })
            .setLargeIcon(largeIcon)
            .setContentIntent(session.sessionActivity)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setSilent(true)
            .setColor(accent)
            .setColorized(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(style)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_skip_previous,
                    "Previous",
                    previousIntent
                )
            )
            .addAction(
                NotificationCompat.Action(
                    if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    if (playing) "Pause" else "Play",
                    playPauseIntent
                )
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_skip_next,
                    "Next",
                    nextIntent
                )
            )

        if (album.isNotBlank() && artist.isNotBlank()) builder.setSubText(album)

        return MediaNotification(
            DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
            builder.build()
        )
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean {
        return false
    }
}

private object ArtworkFrame {

    fun render(
        session: MediaSession,
        artworkUri: Uri?,
        accent: Int,
        playing: Boolean
    ): Bitmap {
        val size = 512
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val artBounds = RectF(70f, 70f, size - 70f, size - 70f)
        val radius = 92f

        val art = artworkUri?.let {
            runCatching {
                session.bitmapLoader.loadBitmap(it).get(1800, TimeUnit.MILLISECONDS)
            }.getOrNull()
        }

        val bloom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            maskFilter = BlurMaskFilter(26f, BlurMaskFilter.Blur.NORMAL)
        }
        repeat(4) { index ->
            bloom.color = accent
            bloom.alpha = 26 + index * 10
            bloom.strokeWidth = 10f + index * 9f
            canvas.drawRoundRect(
                RectF(
                    artBounds.left - index * 5f,
                    artBounds.top - index * 5f,
                    artBounds.right + index * 5f,
                    artBounds.bottom + index * 5f
                ),
                radius + index * 5f,
                radius + index * 5f,
                bloom
            )
        }

        val clip = Path().apply { addRoundRect(artBounds, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        if (art != null) {
            val shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix().apply {
                setRectToRect(
                    RectF(0f, 0f, art.width.toFloat(), art.height.toFloat()),
                    artBounds,
                    Matrix.ScaleToFit.CENTER
                )
            }
            shader.setLocalMatrix(matrix)
            canvas.drawRect(artBounds, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        } else {
            canvas.drawRect(
                artBounds,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        artBounds.left,
                        artBounds.top,
                        artBounds.right,
                        artBounds.bottom,
                        accent,
                        Color.rgb(16, 11, 22),
                        Shader.TileMode.CLAMP
                    )
                }
            )
        }
        canvas.drawRect(
            artBounds,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    artBounds.left,
                    artBounds.top,
                    artBounds.left,
                    artBounds.bottom,
                    Color.argb(70, 255, 255, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
        )
        if (playing) {
            val sweep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 120f
                color = Color.argb(22, 255, 255, 255)
                maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(size / 2f, size * 0.18f, 150f, sweep)
        }
        canvas.restore()

        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.argb(150, 255, 255, 255)
        }
        canvas.drawRoundRect(artBounds, radius, radius, rim)

        val accentRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = accent
            alpha = 150
            maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(artBounds, radius, radius, accentRim)

        return output
    }
}

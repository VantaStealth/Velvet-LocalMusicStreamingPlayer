package com.velvet.music.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class LyricLine(val timeMs: Long, val text: String)

object Lyrics {
    private val stamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val tag = Regex("""\[[^\]]*]""")

    fun parse(body: String): List<LyricLine> = buildList {
        for (raw in body.lineSequence()) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = tag.replace(raw, "").trim()
            if (text.isEmpty()) continue
            for (match in stamps) {
                val minutes = match.groupValues[1].toIntOrNull() ?: continue
                val seconds = match.groupValues[2].toIntOrNull() ?: continue
                val fraction = match.groupValues[3]
                    .ifEmpty { "0" }
                    .padEnd(3, '0')
                    .take(3)
                    .toIntOrNull() ?: 0
                add(LyricLine(minutes * 60_000L + seconds * 1_000L + fraction, text))
            }
        }
    }.sortedBy { it.timeMs }

    fun load(context: Context, mediaId: String): List<LyricLine> {
        return runCatching {
            val uri = Uri.parse(mediaId)
            if (uri.scheme != "content") return emptyList()
            val track = DocumentFile.fromSingleUri(context, uri) ?: return emptyList()
            val parent = track.parentFile ?: return emptyList()
            val base = track.name?.substringBeforeLast('.') ?: return emptyList()
            val sidecar = parent.findFile("$base.lrc") ?: parent.findFile("$base.LRC")
                ?: return emptyList()
            val body = context.contentResolver.openInputStream(sidecar.uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return emptyList()
            parse(body)
        }.getOrDefault(emptyList())
    }
}

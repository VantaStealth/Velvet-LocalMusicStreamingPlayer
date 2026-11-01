package com.velvet.music.data

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("velvet_prefs", Context.MODE_PRIVATE)

    var folderUri: String?
        get() = sp.getString("folder_uri", null)
        set(value) = sp.edit { putString("folder_uri", value) }

    var folderName: String?
        get() = sp.getString("folder_name", null)
        set(value) = sp.edit { putString("folder_name", value) }

    var accent: VelvetAccent
        get() = runCatching { VelvetAccent.valueOf(sp.getString("accent", null) ?: "") }
            .getOrDefault(VelvetAccent.Amethyst)
        set(value) = sp.edit { putString("accent", value.name) }

    var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit { putStringSet("favorites", value) }

    var queueIds: String?
        get() = sp.getString("queue_ids", null)
        set(value) = sp.edit { putString("queue_ids", value) }

    var queueIndex: Int
        get() = sp.getInt("queue_index", 0)
        set(value) = sp.edit { putInt("queue_index", value) }

    var queuePosition: Long
        get() = sp.getLong("queue_position", 0L)
        set(value) = sp.edit { putLong("queue_position", value) }

    var shuffle: Boolean
        get() = sp.getBoolean("shuffle", false)
        set(value) = sp.edit { putBoolean("shuffle", value) }

    var repeatMode: Int
        get() = sp.getInt("repeat_mode", 0)
        set(value) = sp.edit { putInt("repeat_mode", value) }

    var spectrumGain: Float
        get() = sp.getFloat("spectrum_gain", 1f)
        set(value) = sp.edit { putFloat("spectrum_gain", value) }

    var appVolume: Float
        get() = sp.getFloat("app_volume", 1f)
        set(value) = sp.edit { putFloat("app_volume", value) }

    fun toggleFavorite(id: String): Boolean {
        val current = favorites.toMutableSet()
        val nowFavorite = if (!current.add(id)) {
            current.remove(id)
            false
        } else true
        favorites = current
        return nowFavorite
    }
}

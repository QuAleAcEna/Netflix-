package com.example.netflix.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores playback progress per profile and movie so it can be resumed later.
 */
class WatchProgressManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("watch_progress", Context.MODE_PRIVATE)

    private fun key(profileId: Int, movieId: Int): String = "progress_${profileId}_$movieId"

    fun saveProgress(profileId: Int, movieId: Int, positionMs: Long) {
        if (profileId < 0 || movieId < 0) return
        prefs.edit().putLong(key(profileId, movieId), positionMs).apply()
    }

    fun getProgress(profileId: Int, movieId: Int): Long {
        if (profileId < 0 || movieId < 0) return 0L
        return prefs.getLong(key(profileId, movieId), 0L)
    }

    fun clearProgress(profileId: Int, movieId: Int) {
        if (profileId < 0 || movieId < 0) return
        prefs.edit().remove(key(profileId, movieId)).apply()
    }
}

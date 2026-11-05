package com.mintfy.app.data.repository

import android.util.Log
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Cache-first loading for a Firestore listener (FR-G2, NFR-02, FR-H2).
 *
 * 1. [startFrom] reads the Room copy and shows it straight away, so a screen has figures before
 *    the network (or Firestore's own start-up) answers.
 * 2. [onLive] passes each listener snapshot on to the screen and saves it into Room for next time.
 *
 * Once a live snapshot has arrived, a slower cache read is ignored, so old data never replaces new.
 * Saving runs one at a time in the background; if several snapshots arrive while one is being saved,
 * only the newest is written. An empty cache is not shown, so a first run waits for real data instead
 * of flashing an empty screen.
 */
internal class CacheFirst<T>(
    private val channel: ProducerScope<List<T>>,
    private val what: String, // for the log only, e.g. "expenses"
    writeCache: suspend (List<T>) -> Unit,
) {
    private val lock = Any()
    private var live = false
    private val toSave = MutableStateFlow<List<T>?>(null)

    init {
        channel.launch {
            toSave.filterNotNull().collect { list ->
                runCatching { writeCache(list) }.onFailure { Log.w(TAG, "Couldn't cache $what", it) }
            }
        }
    }

    fun startFrom(readCache: suspend () -> List<T>) {
        channel.launch {
            val cached = runCatching { readCache() }
                .onFailure { Log.w(TAG, "Couldn't read cached $what", it) }
                .getOrDefault(emptyList())
            synchronized(lock) {
                if (!live && cached.isNotEmpty()) channel.trySend(cached)
            }
        }
    }

    fun onLive(list: List<T>) {
        synchronized(lock) {
            live = true
            channel.trySend(list)
        }
        toSave.value = list
    }

    private companion object {
        const val TAG = "CacheFirst"
    }
}

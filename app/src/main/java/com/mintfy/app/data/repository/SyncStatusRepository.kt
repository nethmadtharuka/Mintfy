package com.mintfy.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** What the small sync indicator shows (FR-G5). */
enum class SyncStatus {
    SYNCED, // online, nothing waiting: no indicator
    SYNCING, // online, changes still being sent
    OFFLINE, // offline, nothing waiting
    OFFLINE_PENDING, // offline with changes kept on this phone
}

fun syncStatus(online: Boolean, pendingWrites: Boolean): SyncStatus = when {
    online && !pendingWrites -> SyncStatus.SYNCED
    online -> SyncStatus.SYNCING
    pendingWrites -> SyncStatus.OFFLINE_PENDING
    else -> SyncStatus.OFFLINE
}

/**
 * Whether the phone is online and whether Firestore still holds changes the server has not
 * confirmed (FR-G3, FR-G5). Firestore sends those changes by itself when the connection returns;
 * this only reports it, so Kavindu knows an entry made in airplane mode is safe and not yet synced.
 */
@Singleton
class SyncStatusRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
) {

    /** True while the phone has a network that can reach the internet. */
    fun observeOnline(): Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        fun current(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(current())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /**
     * True while some change has not reached the server yet. Firestore's waitForPendingWrites()
     * finishes at once when nothing is waiting, so a short check tells the two cases apart; while
     * something is waiting, it waits for the sync itself. Checked every few seconds, which is cheap
     * because it never reads any documents.
     */
    fun observePendingWrites(): Flow<Boolean> = flow {
        while (true) {
            val synced = withTimeoutOrNull(QUICK_CHECK_MS) { awaitPendingWrites() } != null
            emit(!synced)
            if (!synced) {
                awaitPendingWrites()
                emit(false)
            }
            delay(RECHECK_MS)
        }
    }.distinctUntilChanged()

    /** Signing out cancels the wait with an error; there is then nothing left to sync. */
    private suspend fun awaitPendingWrites() {
        runCatching { firestore.waitForPendingWrites().await() }
    }

    private companion object {
        const val QUICK_CHECK_MS = 500L
        const val RECHECK_MS = 2_000L
    }
}

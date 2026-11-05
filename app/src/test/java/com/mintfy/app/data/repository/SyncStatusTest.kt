package com.mintfy.app.data.repository

import com.mintfy.app.ui.components.syncIndicatorText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SyncStatusTest {

    @Test
    fun `status follows connectivity and waiting changes`() {
        assertEquals(SyncStatus.SYNCED, syncStatus(online = true, pendingWrites = false))
        assertEquals(SyncStatus.SYNCING, syncStatus(online = true, pendingWrites = true))
        assertEquals(SyncStatus.OFFLINE, syncStatus(online = false, pendingWrites = false))
        assertEquals(SyncStatus.OFFLINE_PENDING, syncStatus(online = false, pendingWrites = true))
    }

    @Test
    fun `the indicator is hidden only when everything is synced`() {
        assertNull(syncIndicatorText(SyncStatus.SYNCED))
        SyncStatus.entries.filter { it != SyncStatus.SYNCED }.forEach { assertNotNull(syncIndicatorText(it)) }
        assertEquals("Offline · not synced", syncIndicatorText(SyncStatus.OFFLINE_PENDING)!!.first)
    }
}

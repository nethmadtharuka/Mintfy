package com.mintfy.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.repository.SyncStatus
import com.mintfy.app.data.repository.SyncStatusRepository
import com.mintfy.app.data.repository.syncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One app-wide sync status for the indicator in every top bar (FR-G5). */
@HiltViewModel
class SyncViewModel @Inject constructor(
    repository: SyncStatusRepository,
) : ViewModel() {

    val status: StateFlow<SyncStatus> = combine(
        repository.observeOnline(),
        repository.observePendingWrites(),
        ::syncStatus,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus.SYNCED)
}

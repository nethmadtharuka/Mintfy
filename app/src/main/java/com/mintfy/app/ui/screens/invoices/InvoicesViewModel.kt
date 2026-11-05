package com.mintfy.app.ui.screens.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.repository.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Everything the Invoices screen and the dashboard card show. */
data class InvoicesUiState(
    val filter: InvoiceFilter = InvoiceFilter.OUTSTANDING,
    val invoices: List<IncomeEntry> = emptyList(),
    val outstanding: OutstandingSummary = OutstandingSummary(0, 0),
    val isLoading: Boolean = true,
    val error: String? = null, // loading failed; offer a retry
    val actionError: String? = null, // marking an invoice received failed; the list stays visible
    val markingId: String? = null, // the invoice being marked received
    val savedOffline: Boolean = false, // the last change is on this phone, waiting to sync
)

private sealed interface Loaded {
    data object Loading : Loaded
    data class Failed(val message: String) : Loaded
    data class Entries(val entries: List<IncomeEntry>) : Loaded
}

private data class Action(val error: String? = null, val markingId: String? = null, val savedOffline: Boolean = false)

/**
 * Freelance invoices (FR-B4) and the outstanding total (FR-B7). Reads and writes ordinary income entries
 * through [IncomeRepository]: an invoice marked received becomes received income in History and in every
 * total, with no copy to keep in step. The list is a live listener, so it also follows edits made elsewhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InvoicesViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(InvoiceFilter.OUTSTANDING)
    private val attempt = MutableStateFlow(0) // bumped by retry() to restart the listener
    private val action = MutableStateFlow(Action())

    private val loaded: StateFlow<Loaded> = attempt
        .flatMapLatest { load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loaded.Loading)

    val uiState: StateFlow<InvoicesUiState> = combine(loaded, filter, action) { l, f, a ->
        when (l) {
            Loaded.Loading -> InvoicesUiState(filter = f, isLoading = true)
            is Loaded.Failed -> InvoicesUiState(filter = f, isLoading = false, error = l.message)
            is Loaded.Entries -> InvoicesUiState(
                filter = f,
                invoices = invoices(l.entries, f),
                outstanding = outstandingSummary(l.entries),
                isLoading = false,
                actionError = a.error,
                markingId = a.markingId,
                savedOffline = a.savedOffline,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvoicesUiState())

    fun onFilterSelected(f: InvoiceFilter) = filter.update { f }

    fun retry() = attempt.update { it + 1 }

    fun onOfflineMessageShown() = action.update { it.copy(savedOffline = false) }

    /** FR-B4 / UC-03: the money for [id] arrived on [receivedOn]; it now counts as received income. */
    fun markReceived(id: String, receivedOn: LocalDate) {
        if (action.value.markingId != null) return
        val entry = (loaded.value as? Loaded.Entries)?.entries?.firstOrNull { it.id == id } ?: return
        when (val result = markReceived(entry, receivedOn, LocalDate.now())) {
            is MarkReceived.Invalid -> action.update { it.copy(error = result.message) }
            is MarkReceived.Ready -> {
                action.update { Action(markingId = id) }
                viewModelScope.launch {
                    incomeRepository.updateEntry(id, result.entry, dateChanged = true)
                        .onSuccess { outcome -> action.update { Action(savedOffline = outcome.pendingSync) } }
                        .onFailure { e -> action.update { Action(error = e.message ?: GENERIC_ERROR) } }
                }
            }
        }
    }

    private fun load(): Flow<Loaded> = incomeRepository.observeFreelanceEntries()
        .map<List<IncomeEntry>, Loaded> { Loaded.Entries(it) }
        .onStart { emit(Loaded.Loading) }
        .catch { e -> emit(Loaded.Failed(e.message ?: "Your invoices couldn't be loaded.")) }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."
    }
}

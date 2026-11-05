package com.mintfy.app.ui.screens.income

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.repository.IncomeRepository
import com.mintfy.app.data.repository.IncomeSourceRepository
import com.mintfy.app.data.repository.SaveOutcome
import com.mintfy.app.data.repository.UserProfileRepository
import com.mintfy.app.ui.screens.expense.parseAmountToCents
import com.mintfy.app.ui.screens.invoices.MarkReceived
import com.mintfy.app.ui.screens.invoices.markReceived
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** The income estimate dialog (FR-B8). Text as typed, so mistakes can be corrected. */
data class EstimateEditorState(
    val min: String = "",
    val max: String = "",
    val minError: String? = null,
    val maxError: String? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
)

/** The edit-source dialog (FR-B1, FR-B3): rename, change the expected salary, or archive. */
data class SourceEditorState(
    val sourceId: String,
    val type: IncomeSourceType,
    val name: String,
    val expectedAmount: String = "",
    val nameError: String? = null,
    val expectedAmountError: String? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
) {
    val isSalary: Boolean get() = type == IncomeSourceType.SALARY
}

/** Everything the Income screen shows. The screen only reads this; it never changes it directly. */
data class IncomeUiState(
    val overview: IncomeOverview? = null, // the last good figures; kept on screen if a later update fails
    val sources: List<IncomeSource> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null, // loading failed; offer a retry
    val estimateEditor: EstimateEditorState? = null, // null when the dialog is closed
    val sourceEditor: SourceEditorState? = null,
    val actionError: String? = null, // "Mark as paid" failed; the screen stays as it was
    val isMarkingPaid: Boolean = false,
    val savedOffline: Boolean = false, // the last change is on this phone, waiting to sync
)

private sealed interface IncomeData {
    data object Loading : IncomeData
    data class Failed(val message: String) : IncomeData
    data class Loaded(val overview: IncomeOverview, val sources: List<IncomeSource>) : IncomeData
}

private data class Dialogs(
    val estimate: EstimateEditorState? = null,
    val source: SourceEditorState? = null,
    val actionError: String? = null,
    val isMarkingPaid: Boolean = false,
    val savedOffline: Boolean = false,
)

/**
 * Figma "04 Income & freelance invoices": received income this month (FR-B2, FR-B6), salary against
 * expected (FR-B3), crypto year-to-date (FR-B9), unpaid invoices kept apart (FR-B7), the estimate
 * (FR-B8) and the user's sources (FR-B1). All live listeners, so the screen follows every change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncomeViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val sourceRepository: IncomeSourceRepository,
    private val profileRepository: UserProfileRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val attempt = MutableStateFlow(0) // bumped by retry() to restart the listeners
    private val dialogs = MutableStateFlow(Dialogs())

    private data class DataState(
        val overview: IncomeOverview? = null,
        val sources: List<IncomeSource> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    // Like the dashboard: keep the last figures visible while restarting or after an error (FR-H2).
    private val data: Flow<DataState> = combine(month, attempt) { m, _ -> m }
        .flatMapLatest(::load)
        .scan(DataState()) { previous, d ->
            when (d) {
                IncomeData.Loading -> previous.copy(isLoading = previous.overview == null, error = null)
                is IncomeData.Failed -> previous.copy(isLoading = false, error = d.message)
                is IncomeData.Loaded -> DataState(d.overview, d.sources, isLoading = false)
            }
        }

    val uiState: StateFlow<IncomeUiState> = combine(data, dialogs) { d, dlg ->
        IncomeUiState(
            overview = d.overview,
            sources = d.sources,
            isLoading = d.isLoading,
            error = d.error,
            estimateEditor = dlg.estimate,
            sourceEditor = dlg.source,
            actionError = dlg.actionError,
            isMarkingPaid = dlg.isMarkingPaid,
            savedOffline = dlg.savedOffline,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomeUiState())

    fun retry() = attempt.update { it + 1 }

    /** Called when the screen comes back into view, so it rolls over to a new month on the 1st. */
    fun refresh() = month.update { YearMonth.now() }

    fun onOfflineMessageShown() = dialogs.update { it.copy(savedOffline = false) }

    // ---------- Freelance invoice: Mark as paid (FR-B4, UC-03) ----------

    /** The soonest-due unpaid invoice was paid today; it now counts as received income everywhere. */
    fun markNextInvoicePaid() {
        if (dialogs.value.isMarkingPaid) return
        val entry = uiState.value.overview?.nextInvoice ?: return
        val id = entry.id ?: return
        val today = LocalDate.now()
        when (val result = markReceived(entry, today, today)) {
            is MarkReceived.Invalid -> dialogs.update { it.copy(actionError = result.message) }
            is MarkReceived.Ready -> {
                dialogs.update { it.copy(isMarkingPaid = true, actionError = null) }
                viewModelScope.launch {
                    incomeRepository.updateEntry(id, result.entry, dateChanged = true)
                        .onSuccess { outcome -> dialogs.update { it.copy(isMarkingPaid = false, savedOffline = outcome.pendingSync) } }
                        .onFailure { e -> dialogs.update { it.copy(isMarkingPaid = false, actionError = e.message ?: GENERIC_ERROR) } }
                }
            }
        }
    }

    // ---------- Income estimate (FR-B8) ----------

    fun openEstimateEditor() {
        val current = uiState.value.overview?.estimate
        dialogs.update {
            it.copy(
                estimate = EstimateEditorState(
                    min = current?.minCents?.let(::formatCents).orEmpty(),
                    max = current?.maxCents?.let(::formatCents).orEmpty(),
                )
            )
        }
    }

    fun onEstimateMinChange(text: String) = updateEstimate { it.copy(min = text, minError = null, error = null) }

    fun onEstimateMaxChange(text: String) = updateEstimate { it.copy(max = text, maxError = null, error = null) }

    fun closeEstimateEditor() = dialogs.update { it.copy(estimate = null) }

    fun saveEstimate() {
        val editor = dialogs.value.estimate ?: return
        if (editor.isSaving) return
        when (val input = checkEstimate(editor.min, editor.max)) {
            is EstimateInput.Invalid -> updateEstimate { it.copy(minError = input.minError, maxError = input.maxError) }
            is EstimateInput.Valid -> {
                updateEstimate { it.copy(isSaving = true, error = null) }
                viewModelScope.launch {
                    profileRepository.updateEstimate(input.minCents, input.maxCents)
                        .onSuccess(::closeAfterSave)
                        .onFailure { e -> updateEstimate { it.copy(isSaving = false, error = e.message ?: GENERIC_ERROR) } }
                }
            }
        }
    }

    // ---------- Income sources (FR-B1, FR-B3) ----------

    fun openSourceEditor(sourceId: String) {
        val source = uiState.value.sources.firstOrNull { it.id == sourceId } ?: return
        dialogs.update {
            it.copy(
                source = SourceEditorState(
                    sourceId = source.id,
                    type = source.type,
                    name = source.name,
                    expectedAmount = source.expectedAmountCents?.let(::formatCents).orEmpty(),
                )
            )
        }
    }

    fun onSourceNameChange(text: String) = updateSource { it.copy(name = text, nameError = null, error = null) }

    fun onSourceExpectedAmountChange(text: String) =
        updateSource { it.copy(expectedAmount = text, expectedAmountError = null, error = null) }

    fun closeSourceEditor() = dialogs.update { it.copy(source = null) }

    fun saveSource() {
        val editor = dialogs.value.source ?: return
        if (editor.isSaving) return
        val nameError = sourceNameError(editor.name)
        val expectedError = if (editor.isSalary) expectedAmountError(editor.expectedAmount) else null
        if (nameError != null || expectedError != null) {
            updateSource { it.copy(nameError = nameError, expectedAmountError = expectedError) }
            return
        }
        updateSource { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            sourceRepository.updateSource(
                id = editor.sourceId,
                name = editor.name,
                expectedAmountCents = if (editor.isSalary) parseAmountToCents(editor.expectedAmount) else null,
            )
                .onSuccess(::closeAfterSave)
                .onFailure { e -> updateSource { it.copy(isSaving = false, error = e.message ?: GENERIC_ERROR) } }
        }
    }

    /** Hidden from the add-income form; income already recorded against it keeps its name. */
    fun archiveSource() {
        val editor = dialogs.value.source ?: return
        if (editor.isSaving) return
        updateSource { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            sourceRepository.archiveSource(editor.sourceId)
                .onSuccess(::closeAfterSave)
                .onFailure { e -> updateSource { it.copy(isSaving = false, error = e.message ?: GENERIC_ERROR) } }
        }
    }

    // ---------- Helpers ----------

    private fun closeAfterSave(outcome: SaveOutcome) =
        dialogs.update { it.copy(estimate = null, source = null, savedOffline = outcome.pendingSync) }

    private fun updateEstimate(change: (EstimateEditorState) -> EstimateEditorState) =
        dialogs.update { d -> d.estimate?.let { d.copy(estimate = change(it)) } ?: d }

    private fun updateSource(change: (SourceEditorState) -> SourceEditorState) =
        dialogs.update { d -> d.source?.let { d.copy(source = change(it)) } ?: d }

    private fun load(m: YearMonth): Flow<IncomeData> {
        val (from, to) = yearToMonthRange(m)
        return combine(
            incomeRepository.observeEntries(from, to),
            incomeRepository.observeFreelanceEntries(),
            sourceRepository.observeSources(),
            // The estimate is optional, so a profile problem must not hide the rest of the screen.
            profileRepository.observeCurrentProfile().catch { emit(null) },
        ) { yearEntries, freelance, sources, profile ->
            IncomeData.Loaded(
                overview = incomeOverview(
                    month = m,
                    yearEntries = yearEntries,
                    freelanceEntries = freelance,
                    sources = sources,
                    estimateMinCents = profile?.estimateMinCents,
                    estimateMaxCents = profile?.estimateMaxCents,
                ),
                sources = sources,
            ) as IncomeData
        }
            .onStart { emit(IncomeData.Loading) }
            .catch { e -> emit(IncomeData.Failed(e.message ?: "Your income couldn't be loaded.")) }
    }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."
    }
}

package com.mintfy.app.ui.screens.income

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.data.repository.IncomeRepository
import com.mintfy.app.data.repository.IncomeSourceRepository
import com.mintfy.app.data.repository.SaveOutcome
import com.mintfy.app.ui.navigation.Routes
import com.mintfy.app.ui.screens.expense.parseAmountToCents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The "New income source" dialog. Null in [AddIncomeUiState.sourceDialog] means it is closed. */
data class NewSourceDialogState(
    val name: String = "",
    val type: IncomeSourceType = IncomeSourceType.SALARY,
    val currency: String = defaultCurrencyFor(IncomeSourceType.SALARY),
    val expectedAmount: String = "",
    val nameError: String? = null,
    val expectedAmountError: String? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
)

/** Everything the Add Income screen shows. The screen only reads this; it never changes it directly. */
data class AddIncomeUiState(
    val sources: List<IncomeSource> = emptyList(),
    val isLoadingSources: Boolean = true,
    val selectedSource: IncomeSource? = null,
    val statusOptions: List<IncomeStatus> = listOf(IncomeStatus.RECEIVED),
    val currencyOptions: List<String> = SUPPORTED_CURRENCIES,
    val input: IncomeInput = IncomeInput(amount = "", date = LocalDate.now()),
    val salaryDifference: String? = null,
    val errors: IncomeFieldErrors = IncomeFieldErrors(),
    val error: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val savedOffline: Boolean = false,
    val isDeleted: Boolean = false,
    val isEditing: Boolean = false,
    val sourceDialog: NewSourceDialogState? = null,
)

/** What the user has typed or picked so far. Choices left null fall back to the source's defaults. */
private data class IncomeForm(
    val sourceId: String? = null,
    val amount: String = "",
    val bonus: String = "",
    val currency: String? = null,
    val rate: String = "",
    val lkrAmount: String = "",
    val lkrEdited: Boolean = false, // once the user types the credited amount, stop suggesting one
    val isLoss: Boolean = false,
    val status: IncomeStatus = IncomeStatus.RECEIVED,
    val date: LocalDate = LocalDate.now(),
    val client: String = "",
    val project: String = "",
    val milestone: String = "",
    val dueDate: LocalDate? = null,
    val note: String = "",
    val errors: IncomeFieldErrors = IncomeFieldErrors(),
    val error: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val savedOffline: Boolean = false,
    val isDeleted: Boolean = false,
    val isEditing: Boolean = false,
    val isLoadingEntry: Boolean = false,
    val loadedDate: LocalDate? = null, // the saved date, so an unchanged one is not rewritten
    val sourceDialog: NewSourceDialogState? = null,
)

/**
 * Add income, or edit a saved entry when opened with an entry ID (FR-C5).
 * When editing, the source is fixed: the rules forbid changing an entry's source type.
 */
@HiltViewModel
class AddIncomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sourceRepository: IncomeSourceRepository,
    private val incomeRepository: IncomeRepository,
) : ViewModel() {

    private val editId: String? = savedStateHandle[Routes.ARG_ID]

    private val form = MutableStateFlow(IncomeForm(isEditing = editId != null, isLoadingEntry = editId != null))

    // null means "still loading"; an empty list means loaded but the user has no sources yet.
    private val sources: StateFlow<List<IncomeSource>?> = sourceRepository.observeSources()
        .map<List<IncomeSource>, List<IncomeSource>?> { it }
        .catch { e ->
            form.update { it.copy(error = e.message ?: "Your income sources couldn't be loaded.") }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<AddIncomeUiState> = combine(form, sources, ::buildState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildState(form.value, null))

    init {
        if (editId != null) {
            viewModelScope.launch {
                incomeRepository.getEntry(editId)
                    .onSuccess { entry -> form.update { it.filledFrom(entry) } }
                    .onFailure { e -> form.update { it.copy(isLoadingEntry = false, error = e.message ?: GENERIC_ERROR) } }
            }
        }
    }

    fun onSourceSelected(id: String) = form.update {
        if (it.isEditing) return@update it // the source of a saved entry can't change
        // Currency, status and the crypto sign depend on the source, so start them fresh.
        it.copy(
            sourceId = id, currency = null, status = IncomeStatus.RECEIVED, isLoss = false,
            lkrEdited = false, lkrAmount = "", errors = IncomeFieldErrors(), error = null,
        )
    }

    fun onStatusSelected(status: IncomeStatus) =
        form.update { it.copy(status = status, errors = it.errors.copy(date = null)) }

    fun onLossSelected(isLoss: Boolean) = form.update { it.copy(isLoss = isLoss) }

    fun onCurrencySelected(code: String) = form.update {
        it.copy(currency = code, lkrEdited = false, lkrAmount = "", errors = it.errors.copy(rate = null, lkrAmount = null))
    }

    fun onAmountChange(text: String) =
        form.update { it.copy(amount = text, errors = it.errors.copy(amount = null), error = null) }

    fun onBonusChange(text: String) = form.update { it.copy(bonus = text, errors = it.errors.copy(bonus = null)) }

    fun onRateChange(text: String) = form.update { it.copy(rate = text, errors = it.errors.copy(rate = null)) }

    /** Clearing the field goes back to the suggested amount. */
    fun onLkrAmountChange(text: String) = form.update {
        it.copy(lkrAmount = text, lkrEdited = text.isNotBlank(), errors = it.errors.copy(lkrAmount = null))
    }

    fun onDateSelected(date: LocalDate) = form.update { it.copy(date = date, errors = it.errors.copy(date = null)) }

    fun onClientChange(text: String) = form.update { it.copy(client = text, errors = it.errors.copy(client = null)) }

    fun onProjectChange(text: String) = form.update { it.copy(project = text, errors = it.errors.copy(project = null)) }

    fun onMilestoneChange(text: String) =
        form.update { it.copy(milestone = text, errors = it.errors.copy(milestone = null)) }

    fun onDueDateSelected(date: LocalDate?) = form.update { it.copy(dueDate = date) }

    fun onNoteChange(text: String) = form.update { it.copy(note = text, errors = it.errors.copy(note = null)) }

    /**
     * Validates, then saves. The button shows a spinner while the server is asked; on a failure the
     * message is shown and everything typed stays on the form so the user can fix it and try again.
     */
    fun save() {
        val current = form.value
        // Checked on the form itself, not the derived uiState, so a fast double tap cannot save twice.
        if (current.isSaving || current.isSaved || current.isLoadingEntry) return

        val state = buildState(current, sources.value)
        val source = state.selectedSource
        if (source == null) {
            val message =
                if (current.isEditing) "This entry's income source is no longer available." else "Add an income source first."
            form.update { it.copy(error = message) }
            return
        }
        when (val check = checkIncome(state.input, source, LocalDate.now())) {
            is IncomeCheck.Invalid -> form.update { it.copy(errors = check.errors, error = null) }
            is IncomeCheck.Valid -> {
                form.update { it.copy(isSaving = true, errors = IncomeFieldErrors(), error = null) }
                viewModelScope.launch {
                    val result = if (editId == null) {
                        incomeRepository.addEntry(check.entry)
                    } else {
                        incomeRepository.updateEntry(editId, check.entry, dateChanged = check.entry.date != current.loadedDate)
                    }
                    finish(result)
                }
            }
        }
    }

    /** Deletes the entry being edited. The screen asks for confirmation first (FR-H5). */
    fun delete() {
        val id = editId ?: return
        val current = form.value
        if (current.isSaving || current.isSaved || current.isLoadingEntry) return
        form.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch { finish(incomeRepository.deleteEntry(id), deleted = true) }
    }

    /** On success the screen sees isSaved and closes; on failure everything typed stays on the form. */
    private fun finish(result: Result<SaveOutcome>, deleted: Boolean = false) {
        result
            .onSuccess { outcome ->
                form.update {
                    it.copy(isSaving = false, isSaved = true, savedOffline = outcome.pendingSync, isDeleted = deleted)
                }
            }
            .onFailure { e -> form.update { it.copy(isSaving = false, error = e.message ?: GENERIC_ERROR) } }
    }

    fun openNewSource() = form.update { it.copy(sourceDialog = NewSourceDialogState()) }

    fun dismissNewSource() = form.update { if (it.sourceDialog?.isSaving == true) it else it.copy(sourceDialog = null) }

    fun onNewSourceNameChange(text: String) = updateDialog { it.copy(name = text, nameError = null, error = null) }

    /** Picking a type also picks its usual currency; the user can still change it. */
    fun onNewSourceTypeSelected(type: IncomeSourceType) =
        updateDialog { it.copy(type = type, currency = defaultCurrencyFor(type), error = null) }

    fun onNewSourceCurrencySelected(code: String) = updateDialog { it.copy(currency = code, error = null) }

    fun onNewSourceExpectedAmountChange(text: String) =
        updateDialog { it.copy(expectedAmount = text, expectedAmountError = null) }

    fun createSource() {
        val dialog = form.value.sourceDialog ?: return
        if (dialog.isSaving) return

        val isSalary = dialog.type == IncomeSourceType.SALARY
        val nameError = sourceNameError(dialog.name)
        val expectedError = if (isSalary) expectedAmountError(dialog.expectedAmount) else null
        val currencyError = currencyError(dialog.currency)
        if (nameError != null || expectedError != null || currencyError != null) {
            updateDialog { it.copy(nameError = nameError, expectedAmountError = expectedError, error = currencyError) }
            return
        }

        updateDialog { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            sourceRepository.addSource(
                name = dialog.name,
                type = dialog.type,
                defaultCurrency = dialog.currency,
                expectedAmountCents = if (isSalary) parseAmountToCents(dialog.expectedAmount) else null,
            )
                .onSuccess { outcome ->
                    // Select the new source straight away; it is already in the list, even offline.
                    form.update { it.copy(sourceDialog = null) }
                    onSourceSelected(outcome.id)
                }
                .onFailure { e -> updateDialog { it.copy(isSaving = false, error = e.message ?: GENERIC_ERROR) } }
        }
    }

    private fun updateDialog(change: (NewSourceDialogState) -> NewSourceDialogState) =
        form.update { f -> f.sourceDialog?.let { f.copy(sourceDialog = change(it)) } ?: f }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."

        fun buildState(f: IncomeForm, sources: List<IncomeSource>?): AddIncomeUiState {
            val list = if (f.isEditing) sources.orEmpty().filter { it.id == f.sourceId } else sources.orEmpty()
            // The user's choice, else the first source (salary sorts first).
            val source = list.firstOrNull { it.id == f.sourceId } ?: list.firstOrNull()
            val currency = f.currency ?: source?.defaultCurrency ?: BASE_CURRENCY
            val isSalary = source?.type == IncomeSourceType.SALARY
            val bonus = if (isSalary) f.bonus else ""
            val lkrAmount = when {
                currency == BASE_CURRENCY -> ""
                f.lkrEdited -> f.lkrAmount
                else -> suggestedLkrAmount(f.amount, bonus, f.rate).orEmpty()
            }
            val input = IncomeInput(
                amount = f.amount,
                bonus = f.bonus,
                currency = currency,
                rate = f.rate,
                lkrAmount = lkrAmount,
                isLoss = f.isLoss,
                status = source?.let { allowedStatus(it.type, f.status) } ?: IncomeStatus.RECEIVED,
                date = f.date,
                client = f.client,
                project = f.project,
                milestone = f.milestone,
                dueDate = f.dueDate,
                note = f.note,
            )
            return AddIncomeUiState(
                sources = list,
                isLoadingSources = sources == null || f.isLoadingEntry,
                selectedSource = source,
                statusOptions = source?.let { statusOptions(it.type) } ?: listOf(IncomeStatus.RECEIVED),
                // Keep a source's own currency selectable even if it is not one of the usual three.
                currencyOptions = (SUPPORTED_CURRENCIES + listOfNotNull(source?.defaultCurrency, currency)).distinct(),
                input = input,
                salaryDifference = salaryDifference(source, input),
                errors = f.errors,
                error = f.error,
                isSaving = f.isSaving,
                isSaved = f.isSaved,
                savedOffline = f.savedOffline,
                isDeleted = f.isDeleted,
                isEditing = f.isEditing,
                sourceDialog = f.sourceDialog,
            )
        }

        /** Puts a saved entry on the form. A foreign-currency entry keeps the LKR amount actually credited. */
        fun IncomeForm.filledFrom(entry: IncomeEntry): IncomeForm {
            val input = incomeInputFor(entry)
            return copy(
                sourceId = entry.sourceId,
                amount = input.amount,
                bonus = input.bonus,
                currency = input.currency,
                rate = input.rate,
                lkrAmount = input.lkrAmount,
                lkrEdited = input.currency != BASE_CURRENCY,
                isLoss = input.isLoss,
                status = input.status,
                date = input.date,
                client = input.client,
                project = input.project,
                milestone = input.milestone,
                dueDate = input.dueDate,
                note = input.note,
                isLoadingEntry = false,
                loadedDate = entry.date,
            )
        }

        /** FR-B3: compare a salary receipt, in LKR, with the expected salary. */
        fun salaryDifference(source: IncomeSource?, input: IncomeInput): String? {
            if (source?.type != IncomeSourceType.SALARY) return null
            val expected = source.expectedAmountCents ?: return null
            val received = if (input.currency == BASE_CURRENCY) {
                val base = parseAmountToCents(input.amount) ?: return null
                base + (if (input.bonus.isBlank()) 0L else parseAmountToCents(input.bonus) ?: return null)
            } else {
                parseAmountToCents(input.lkrAmount) ?: return null
            }
            return salaryDifferenceLabel(received, expected)
        }
    }
}

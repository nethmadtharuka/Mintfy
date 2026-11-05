package com.mintfy.app.data.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * EXPECTED (agreed) -> INVOICED (invoice sent) -> RECEIVED (money arrived).
 * Only RECEIVED counts as income or savings (FR-B6, FR-E6).
 */
enum class IncomeStatus { EXPECTED, INVOICED, RECEIVED }

/**
 * A document at users/{uid}/incomeEntries: one receipt, or one freelance amount still owed.
 * Money is whole cents. Every total uses [lkrAmountCents]; the original amount, currency and the
 * rate entered at transfer are kept so any figure can be audited (FR-B5, NFR-05).
 * Crypto amounts are signed; everything else is positive. Validated before saving (IncomeValidation).
 */
data class IncomeEntry(
    val sourceId: String,
    val sourceType: IncomeSourceType, // copied from the source; the rules keep it fixed
    val status: IncomeStatus,
    val date: LocalDate, // date received; for an unpaid entry, when payment is expected
    val currency: String,
    val originalAmountCents: Long,
    val exchangeRate: BigDecimal, // LKR per unit, exactly 1 for LKR
    val lkrAmountCents: Long,
    val baseAmountCents: Long? = null, // salary only (FR-B3)
    val bonusAmountCents: Long? = null,
    val client: String? = null, // freelance only (FR-B4)
    val project: String? = null,
    val milestone: String? = null,
    val dueDate: LocalDate? = null,
    val note: String? = null,
    val id: String? = null, // null until saved
)

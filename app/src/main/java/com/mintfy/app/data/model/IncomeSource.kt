package com.mintfy.app.data.model

/** Where money comes from (SRS FR-B1). The type is set once and never changes (firestore.rules). */
enum class IncomeSourceType {
    SALARY, FREELANCE, ADSENSE, CRYPTO, OTHER;

    /** Only freelance and other income can be waiting for payment (FR-E6, firestore.rules). */
    val allowsUnpaid: Boolean get() = this == FREELANCE || this == OTHER

    /** Crypto results are signed: a gain or a loss (FR-B9). */
    val isSigned: Boolean get() = this == CRYPTO
}

/**
 * A document at users/{uid}/incomeSources/{id}.
 * Sources are archived, never deleted, because income entries refer to them.
 */
data class IncomeSource(
    val id: String,
    val name: String,
    val type: IncomeSourceType,
    val defaultCurrency: String, // pre-selects the currency on the add-income form (FR-B5)
    val expectedAmountCents: Long? = null, // expected salary in LKR cents (FR-B3)
    val archived: Boolean = false,
)

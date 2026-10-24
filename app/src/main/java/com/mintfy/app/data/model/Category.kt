package com.mintfy.app.data.model

/** Whether a cost is a fixed obligation or something Kavindu could choose to cut (SRS FR-D2). */
enum class SpendingType { COMMITTED, DISCRETIONARY }

/**
 * A document at users/{uid}/categories/{id}.
 * Categories are archived, never deleted, so old expenses keep their label.
 */
data class Category(
    val id: String,
    val name: String,
    val spendingType: SpendingType,
    val icon: String? = null,
    val builtIn: Boolean = false, // named builtIn, not isDefault: the Firestore mapper would drop "is"
    val archived: Boolean = false,
)

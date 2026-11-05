package com.mintfy.app.data.model

/** How an expense was paid (SRS FR-C2). ACCOUNT covers named accounts such as a secondary account. */
enum class PaymentMethodType { CASH, CARD, ACCOUNT }

/** A document at users/{uid}/paymentMethods/{id}. Archived, never deleted. */
data class PaymentMethod(
    val id: String,
    val name: String,
    val type: PaymentMethodType,
    val archived: Boolean = false,
)

package com.mintfy.app.data.model

import com.google.firebase.Timestamp

/** The profile document at users/{uid}. Money is whole LKR cents. */
data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String? = null,
    val baseCurrency: String = "LKR",
    val estimateMinCents: Long? = null,
    val estimateMaxCents: Long? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
)

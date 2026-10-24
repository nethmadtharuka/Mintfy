package com.mintfy.app.data.local

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the category and payment method of the last saved expense, so the next one is pre-selected
 * (FR-C3). Kept on the phone, not in Firestore: it is only a form convenience, and storing it in the cloud
 * would add an extra write to every expense (schema decision D-10). Kept per account, so two people
 * signing in on the same phone never see each other's choices.
 */
@Singleton
class ExpensePreferences @Inject constructor(
    @ApplicationContext context: Context,
    private val auth: FirebaseAuth,
) {
    private val prefs = context.getSharedPreferences("expense_preferences", Context.MODE_PRIVATE)

    val lastCategoryId: String?
        get() = key(LAST_CATEGORY)?.let { prefs.getString(it, null) }

    val lastPaymentMethodId: String?
        get() = key(LAST_PAYMENT_METHOD)?.let { prefs.getString(it, null) }

    fun remember(categoryId: String, paymentMethodId: String) {
        val categoryKey = key(LAST_CATEGORY) ?: return
        val paymentKey = key(LAST_PAYMENT_METHOD) ?: return
        prefs.edit()
            .putString(categoryKey, categoryId)
            .putString(paymentKey, paymentMethodId)
            .apply()
    }

    private fun key(name: String): String? = auth.currentUser?.uid?.let { "${name}_$it" }

    private companion object {
        const val LAST_CATEGORY = "last_category_id"
        const val LAST_PAYMENT_METHOD = "last_payment_method_id"
    }
}

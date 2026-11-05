package com.mintfy.app.ui.screens.categories

import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.SpendingType

// Plain Kotlin so it runs in JVM unit tests. The length limit mirrors firestore.rules (categories.name),
// so a name that passes here is not rejected by the server.

const val MAX_CATEGORY_NAME_LENGTH = 40

/**
 * Null when [name] can be saved. [others] are the user's other active categories: two with the same
 * name (ignoring case and spaces) would be impossible to tell apart on the add-expense form.
 * [editingId] is the category being renamed, so keeping its own name is not a clash.
 */
fun categoryNameError(name: String, others: List<Category>, editingId: String? = null): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Enter a name for this category."
        trimmed.length > MAX_CATEGORY_NAME_LENGTH -> "Use at most $MAX_CATEGORY_NAME_LENGTH characters."
        others.any { it.id != editingId && it.name.trim().equals(trimmed, ignoreCase = true) } ->
            "You already have a category called \"${trimmed}\"."
        else -> null
    }
}

/** Figma "08 Categories": two lists, committed first. */
data class CategoryGroups(val committed: List<Category>, val discretionary: List<Category>)

fun groupCategories(categories: List<Category>): CategoryGroups {
    val (committed, discretionary) = categories.partition { it.spendingType == SpendingType.COMMITTED }
    return CategoryGroups(committed, discretionary)
}

fun SpendingType.displayName(): String = when (this) {
    SpendingType.COMMITTED -> "Committed"
    SpendingType.DISCRETIONARY -> "Discretionary"
}

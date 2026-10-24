package com.mintfy.app.data.model

/**
 * Categories and payment methods every new user starts with, so the first expense needs no setup (FR-D1).
 * The IDs are fixed, which makes seeding safe to repeat: the same document is never created twice.
 * Types follow the Firestore schema document, section 3.6; the user can change them later (FR-D4).
 */
object DefaultReferenceData {

    val categories = listOf(
        Category("default_rent", "Rent", SpendingType.COMMITTED, icon = "home", builtIn = true),
        Category("default_utilities", "Utilities", SpendingType.COMMITTED, icon = "bolt", builtIn = true),
        Category("default_subscriptions", "Subscriptions", SpendingType.COMMITTED, icon = "subscriptions", builtIn = true),
        Category("default_gym", "Gym & Health", SpendingType.COMMITTED, icon = "fitness", builtIn = true),
        Category("default_groceries", "Groceries", SpendingType.COMMITTED, icon = "groceries", builtIn = true),
        Category("default_food_coffee", "Food & Coffee", SpendingType.DISCRETIONARY, icon = "coffee", builtIn = true),
        Category("default_dining", "Dining & Delivery", SpendingType.DISCRETIONARY, icon = "dining", builtIn = true),
        Category("default_transport", "Transport", SpendingType.DISCRETIONARY, icon = "transport", builtIn = true),
        Category("default_entertainment", "Entertainment", SpendingType.DISCRETIONARY, icon = "entertainment", builtIn = true),
        Category("default_shopping", "Shopping", SpendingType.DISCRETIONARY, icon = "shopping", builtIn = true),
        Category("default_other", "Other", SpendingType.DISCRETIONARY, icon = "other", builtIn = true),
    )

    // Cash and the secondary account are the channels Kavindu's bank summary never saw (E-13).
    val paymentMethods = listOf(
        PaymentMethod("default_cash", "Cash", PaymentMethodType.CASH),
        PaymentMethod("default_card", "Bank card", PaymentMethodType.CARD),
        PaymentMethod("default_secondary", "Secondary account", PaymentMethodType.ACCOUNT),
    )
}

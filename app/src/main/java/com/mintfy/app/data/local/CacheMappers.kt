package com.mintfy.app.data.local

import com.google.firebase.Timestamp
import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.Expense
import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.data.model.PaymentMethod
import com.mintfy.app.data.model.PaymentMethodType
import com.mintfy.app.data.model.RecurringExpense
import com.mintfy.app.data.model.RecurringFrequency
import com.mintfy.app.data.model.SpendingType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Date

// Converts between the app's models and the cache rows. Plain Kotlin, so a JVM test checks that
// every field survives the round trip. A row with an unknown enum value (from an older or newer
// app version) maps to null and is skipped, like a malformed Firestore document.

fun Expense.toCached(uid: String) = CachedExpense(
    uid = uid,
    id = id,
    amountCents = amountCents,
    dateMillis = date.toDate().time,
    categoryId = categoryId,
    paymentMethodId = paymentMethodId,
    note = note,
    recurringId = recurringId,
)

fun CachedExpense.toModel() = Expense(
    id = id,
    amountCents = amountCents,
    date = Timestamp(Date(dateMillis)),
    categoryId = categoryId,
    paymentMethodId = paymentMethodId,
    note = note,
    recurringId = recurringId,
)

/** Null for an entry that was never saved; only saved entries are cached. */
fun IncomeEntry.toCached(uid: String): CachedIncomeEntry? = CachedIncomeEntry(
    uid = uid,
    id = id ?: return null,
    sourceId = sourceId,
    sourceType = sourceType.name,
    status = status.name,
    dateEpochDay = date.toEpochDay(),
    currency = currency,
    originalAmountCents = originalAmountCents,
    exchangeRate = exchangeRate.toPlainString(),
    lkrAmountCents = lkrAmountCents,
    baseAmountCents = baseAmountCents,
    bonusAmountCents = bonusAmountCents,
    client = client,
    project = project,
    milestone = milestone,
    dueDateEpochDay = dueDate?.toEpochDay(),
    note = note,
)

fun CachedIncomeEntry.toModel(): IncomeEntry? = IncomeEntry(
    id = id,
    sourceId = sourceId,
    sourceType = enumOrNull<IncomeSourceType>(sourceType) ?: return null,
    status = enumOrNull<IncomeStatus>(status) ?: return null,
    date = LocalDate.ofEpochDay(dateEpochDay),
    currency = currency,
    originalAmountCents = originalAmountCents,
    exchangeRate = BigDecimal(exchangeRate),
    lkrAmountCents = lkrAmountCents,
    baseAmountCents = baseAmountCents,
    bonusAmountCents = bonusAmountCents,
    client = client,
    project = project,
    milestone = milestone,
    dueDate = dueDateEpochDay?.let(LocalDate::ofEpochDay),
    note = note,
)

fun Category.toCached(uid: String, position: Int) =
    CachedCategory(uid, id, position, name, spendingType.name, icon, builtIn, archived)

fun CachedCategory.toModel(): Category? = Category(
    id = id,
    name = name,
    spendingType = enumOrNull<SpendingType>(spendingType) ?: return null,
    icon = icon,
    builtIn = builtIn,
    archived = archived,
)

fun PaymentMethod.toCached(uid: String, position: Int) =
    CachedPaymentMethod(uid, id, position, name, type.name, archived)

fun CachedPaymentMethod.toModel(): PaymentMethod? =
    PaymentMethod(id, name, enumOrNull<PaymentMethodType>(type) ?: return null, archived)

fun IncomeSource.toCached(uid: String, position: Int) =
    CachedIncomeSource(uid, id, position, name, type.name, defaultCurrency, expectedAmountCents, archived)

fun CachedIncomeSource.toModel(): IncomeSource? = IncomeSource(
    id = id,
    name = name,
    type = enumOrNull<IncomeSourceType>(type) ?: return null,
    defaultCurrency = defaultCurrency,
    expectedAmountCents = expectedAmountCents,
    archived = archived,
)

fun RecurringExpense.toCached(uid: String, position: Int) = CachedRecurring(
    uid = uid,
    id = id,
    position = position,
    name = name,
    categoryId = categoryId,
    paymentMethodId = paymentMethodId,
    amountCents = amountCents,
    frequency = frequency.name,
    nextDueEpochDay = nextDueDate.toEpochDay(),
    active = active,
)

fun CachedRecurring.toModel(): RecurringExpense? = RecurringExpense(
    id = id,
    name = name,
    categoryId = categoryId,
    paymentMethodId = paymentMethodId,
    amountCents = amountCents,
    frequency = enumOrNull<RecurringFrequency>(frequency) ?: return null,
    nextDueDate = LocalDate.ofEpochDay(nextDueEpochDay),
    active = active,
)

private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
    enumValues<E>().firstOrNull { it.name == name }

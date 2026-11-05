package com.mintfy.app.ui.screens.categories

import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.SpendingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryValidationTest {

    private val rent = Category("cat_rent", "Rent", SpendingType.COMMITTED, builtIn = true)
    private val food = Category("cat_food", "Food & Coffee", SpendingType.DISCRETIONARY, builtIn = true)
    private val gym = Category("c1", "Gym", SpendingType.COMMITTED)
    private val all = listOf(rent, food, gym)

    @Test
    fun `a new unique name is accepted`() {
        assertNull(categoryNameError("Pets", all))
    }

    @Test
    fun `blank names are rejected`() {
        assertNotNull(categoryNameError("   ", all))
    }

    @Test
    fun `names longer than the rules allow are rejected`() {
        assertNull(categoryNameError("a".repeat(MAX_CATEGORY_NAME_LENGTH), all))
        assertNotNull(categoryNameError("a".repeat(MAX_CATEGORY_NAME_LENGTH + 1), all))
    }

    @Test
    fun `a name already used is rejected, ignoring case and spaces`() {
        assertNotNull(categoryNameError("  gym ", all))
        assertNotNull(categoryNameError("FOOD & COFFEE", all))
    }

    @Test
    fun `renaming a category may keep its own name`() {
        assertNull(categoryNameError("Gym", all, editingId = "c1"))
        assertNotNull(categoryNameError("Rent", all, editingId = "c1"))
    }

    @Test
    fun `categories are grouped by spending type in their order`() {
        val groups = groupCategories(all)
        assertEquals(listOf(rent, gym), groups.committed)
        assertEquals(listOf(food), groups.discretionary)
    }
}

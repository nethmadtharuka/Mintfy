
package com.mintfy.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

import com.mintfy.app.ui.components.MainTab
import com.mintfy.app.ui.components.MintfyBottomBar
import com.mintfy.app.ui.screens.auth.AuthViewModel
import com.mintfy.app.ui.screens.auth.LoginScreen
import com.mintfy.app.ui.screens.auth.RegisterScreen
import com.mintfy.app.ui.screens.auth.Session
import com.mintfy.app.ui.screens.categories.CategoriesScreen
import com.mintfy.app.ui.screens.recurring.RecurringScreen

import com.mintfy.app.ui.screens.dashboard.DashboardScreen
import com.mintfy.app.ui.screens.expense.AddExpenseScreen
import com.mintfy.app.ui.screens.history.EntryKind
import com.mintfy.app.ui.screens.history.HistoryScreen
import com.mintfy.app.ui.screens.income.AddIncomeScreen
import com.mintfy.app.ui.screens.income.IncomeScreen
import com.mintfy.app.ui.screens.invoices.InvoicesScreen
import com.mintfy.app.ui.screens.invoices.UnpaidInvoicesCard

import com.mintfy.app.ui.screens.goal.GoalViewModel
import com.mintfy.app.ui.screens.goal.SavingsGoalScreen


object Routes {

    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val ADD_EXPENSE = "add_expense"
    const val ADD_INCOME = "add_income"
    const val HISTORY = "history"
    const val INVOICES = "invoices"
    const val INCOME = "income"

    /** Optional entry ID for editing an existing entry. */
    const val ARG_ID = "id"

    const val SAVINGS_GOAL = "savings_goal"
    const val CATEGORIES = "categories"
    const val RECURRING = "recurring"

    /** Optional recurring item to record this period's expense for (FR-C6). */
    const val ARG_RECURRING_ID = "recurringId"

    fun editExpense(id: String) =
        "$ADD_EXPENSE?$ARG_ID=${Uri.encode(id)}"

    fun recordRecurring(recurringId: String) =
        "$ADD_EXPENSE?$ARG_RECURRING_ID=${Uri.encode(recurringId)}"

    fun editIncome(id: String) =
        "$ADD_INCOME?$ARG_ID=${Uri.encode(id)}"
}


private val optionalId = listOf(
    navArgument(Routes.ARG_ID) {
        type = NavType.StringType
        nullable = true
    }
)

private val authRoutes = setOf(
    Routes.LOGIN,
    Routes.REGISTER
)

/** The screen each bottom-navigation tab opens. */
private val MainTab.route: String
    get() = when (this) {
        MainTab.DASHBOARD -> Routes.DASHBOARD
        MainTab.INCOME -> Routes.INCOME
        MainTab.HISTORY -> Routes.HISTORY
        MainTab.GOAL -> Routes.SAVINGS_GOAL
    }


@Composable
fun MintfyNavHost(
    // Activity-scoped authentication ViewModel.
    authViewModel: AuthViewModel = hiltViewModel(),
) {

    val session by authViewModel.session.collectAsStateWithLifecycle()

    if (session == Session.Loading) {

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        return
    }

    val navController = rememberNavController()

    val startDestination = remember {
        if (session is Session.SignedIn) {
            Routes.DASHBOARD
        } else {
            Routes.LOGIN
        }
    }

    // Handle authentication changes.
    LaunchedEffect(session) {

        val route = navController.currentDestination?.route

        when {

            session is Session.SignedIn &&
                    route in authRoutes -> {

                navController.replaceAll(Routes.DASHBOARD)
            }

            session == Session.SignedOut &&
                    route !in authRoutes -> {

                navController.replaceAll(Routes.LOGIN)
                navController.clearTabStacks()
            }
        }
    }

    val openAddExpense = {
        navController.navigate(Routes.ADD_EXPENSE) {
            launchSingleTop = true
        }
    }

    // Figma "Bottom nav", shared by the four top-level screens.
    val tabBar: @Composable (MainTab) -> Unit = { tab ->
        MintfyBottomBar(
            selected = tab,
            onSelect = navController::openTab,
            onAddExpense = openAddExpense,
        )
    }


    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        composable(Routes.LOGIN) {

            LoginScreen(
                viewModel = authViewModel,

                onRegisterClick = {
                    authViewModel.clearErrors()

                    navController.navigate(Routes.REGISTER) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.REGISTER) {

            RegisterScreen(
                viewModel = authViewModel,

                onSignInClick = {
                    authViewModel.clearErrors()
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.DASHBOARD) {

            // Never render signed-in content without a session.
            if (session !is Session.SignedIn) {
                return@composable
            }

            // Obtain Savings Goal ViewModel using Hilt.
            val goalViewModel: GoalViewModel = hiltViewModel()

            DashboardScreen(

                // FR-F4: Pass goal data to Dashboard.
                goalViewModel = goalViewModel,

                onAddExpense = openAddExpense,

                onAddIncome = {
                    navController.navigate(Routes.ADD_INCOME) {
                        launchSingleTop = true
                    }
                },

                // Goal is a tab, so the goal card opens it the way the bottom bar does.
                onSavingsGoal = { navController.openTab(MainTab.GOAL) },

                onSignOut = authViewModel::signOut,

                onCategories = {
                    navController.navigate(Routes.CATEGORIES) {
                        launchSingleTop = true
                    }
                },

                onRecurring = {
                    navController.navigate(Routes.RECURRING) {
                        launchSingleTop = true
                    }
                },

                unpaidInvoices = {
                    UnpaidInvoicesCard(
                        onViewInvoices = {
                            navController.navigate(Routes.INVOICES) {
                                launchSingleTop = true
                            }
                        }
                    )
                },

                bottomBar = { tabBar(MainTab.DASHBOARD) },
            )
        }

        composable(Routes.INVOICES) { entry ->

            if (session !is Session.SignedIn) {
                return@composable
            }

            InvoicesScreen(

                onBack = navController.closer(entry),

                onOpenInvoice = { id ->

                    navController.navigate(Routes.editIncome(id)) {
                        launchSingleTop = true
                    }
                },

                onAddIncome = {

                    navController.navigate(Routes.ADD_INCOME) {
                        launchSingleTop = true
                    }
                },
            )
        }

        // Bottom-navigation tab: no back arrow; the system back button returns to the Dashboard.
        composable(Routes.HISTORY) {

            if (session !is Session.SignedIn) {
                return@composable
            }

            HistoryScreen(

                bottomBar = { tabBar(MainTab.HISTORY) },

                onOpenEntry = { row ->

                    val route = when (row.kind) {

                        EntryKind.EXPENSE ->
                            Routes.editExpense(row.id)

                        EntryKind.INCOME ->
                            Routes.editIncome(row.id)
                    }

                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },

                onAddExpense = openAddExpense,

                onAddIncome = {
                    navController.navigate(Routes.ADD_INCOME) {
                        launchSingleTop = true
                    }
                },
            )
        }


        composable(
            "${Routes.ADD_EXPENSE}?${Routes.ARG_ID}={${Routes.ARG_ID}}" +
                    "&${Routes.ARG_RECURRING_ID}={${Routes.ARG_RECURRING_ID}}",
            arguments = optionalId + navArgument(Routes.ARG_RECURRING_ID) {
                type = NavType.StringType
                nullable = true
            }
        ) { entry ->

            if (session !is Session.SignedIn) {
                return@composable
            }

            val close = navController.closer(entry)

            AddExpenseScreen(
                onBack = close,
                onSaved = close
            )
        }

        composable(
            "${Routes.ADD_INCOME}?${Routes.ARG_ID}={${Routes.ARG_ID}}",
            arguments = optionalId
        ) { entry ->

            if (session !is Session.SignedIn) {
                return@composable
            }

            val close = navController.closer(entry)

            AddIncomeScreen(
                onBack = close,
                onSaved = close
            )
        }
        // Figma "04 Income & freelance invoices" (FR-B1 to FR-B9). Bottom-navigation tab.
        composable(Routes.INCOME) {

            if (session !is Session.SignedIn) {
                return@composable
            }

            IncomeScreen(
                bottomBar = { tabBar(MainTab.INCOME) },

                onAddIncome = {
                    navController.navigate(Routes.ADD_INCOME) {
                        launchSingleTop = true
                    }
                },

                onOpenEntry = { id ->
                    navController.navigate(Routes.editIncome(id)) {
                        launchSingleTop = true
                    }
                },

                onViewInvoices = {
                    navController.navigate(Routes.INVOICES) {
                        launchSingleTop = true
                    }
                },
            )
        }

        // Figma "08 Categories" (FR-D2, FR-D4), opened from the dashboard's "⋮" menu.
        composable(Routes.CATEGORIES) { entry ->

            if (session !is Session.SignedIn) {
                return@composable
            }

            CategoriesScreen(onBack = navController.closer(entry))
        }

        // Figma "09 Recurring" (FR-C6), opened from the dashboard's "⋮" menu.
        composable(Routes.RECURRING) { entry ->

            if (session !is Session.SignedIn) {
                return@composable
            }

            RecurringScreen(
                onBack = navController.closer(entry),

                // Opens Add expense pre-filled with this item; the amount can still be changed (FR-D3).
                onRecord = { id ->
                    navController.navigate(Routes.recordRecurring(id)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        // Bottom-navigation tab.
        composable(Routes.SAVINGS_GOAL) {

            if (session !is Session.SignedIn) {
                return@composable
            }

            val goalViewModel: GoalViewModel = hiltViewModel()

            SavingsGoalScreen(
                viewModel = goalViewModel,
                bottomBar = { tabBar(MainTab.GOAL) },
            )
        }
    }
}


/**
 * Closes the current navigation entry.
 * Returns to whichever screen opened it.
 */
private fun NavHostController.closer(
    entry: NavBackStackEntry
): () -> Unit = {

    if (currentBackStackEntry == entry) {
        popBackStack()
    }
}


/**
 * Switches to a bottom-navigation tab.
 * The Dashboard stays at the bottom of the stack, so back from any tab returns to it.
 * The tab being left keeps its own screens (saveState) and they come back when it is
 * selected again (restoreState), e.g. Income → Invoices stays open after visiting History.
 */
private fun NavHostController.openTab(tab: MainTab) = navigate(tab.route) {

    popUpTo(Routes.DASHBOARD) {
        saveState = true
    }

    launchSingleTop = true
    restoreState = true
}


/**
 * Forgets the tabs' saved screens after sign-out,
 * so the next account never gets the previous account's screens back.
 */
private fun NavHostController.clearTabStacks() {

    MainTab.entries
        .filter { it != MainTab.DASHBOARD }
        .forEach { clearBackStack(it.route) }
}


/**
 * Clears the navigation back stack when authentication changes.
 */
private fun NavHostController.replaceAll(
    route: String
) = navigate(route) {

    popUpTo(graph.id) {
        inclusive = true
    }

    launchSingleTop = true
}

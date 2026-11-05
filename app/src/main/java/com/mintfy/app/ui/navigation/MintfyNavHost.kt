
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

import com.mintfy.app.ui.screens.auth.AuthViewModel
import com.mintfy.app.ui.screens.auth.LoginScreen
import com.mintfy.app.ui.screens.auth.RegisterScreen
import com.mintfy.app.ui.screens.auth.Session

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

    fun editExpense(id: String) =
        "$ADD_EXPENSE?$ARG_ID=${Uri.encode(id)}"

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
            }
        }
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

                onAddExpense = {
                    navController.navigate(Routes.ADD_EXPENSE) {
                        launchSingleTop = true
                    }
                },

                onAddIncome = {
                    navController.navigate(Routes.ADD_INCOME) {
                        launchSingleTop = true
                    }
                },

                onIncome = {
                    navController.navigate(Routes.INCOME) {
                        launchSingleTop = true
                    }
                },

                onHistory = {
                    navController.navigate(Routes.HISTORY) {
                        launchSingleTop = true
                    }
                },

                onSavingsGoal = {
                    navController.navigate(Routes.SAVINGS_GOAL) {
                        launchSingleTop = true
                    }
                },

                onSignOut = authViewModel::signOut,

                unpaidInvoices = {
                    UnpaidInvoicesCard(
                        onViewInvoices = {
                            navController.navigate(Routes.INVOICES) {
                                launchSingleTop = true
                            }
                        }
                    )
                },
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

        composable(Routes.HISTORY) { entry ->

            if (session !is Session.SignedIn) {
                return@composable
            }

            HistoryScreen(

                onBack = navController.closer(entry),

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

                onAddExpense = {
                    navController.navigate(Routes.ADD_EXPENSE) {
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


        composable(
            "${Routes.ADD_EXPENSE}?${Routes.ARG_ID}={${Routes.ARG_ID}}",
            arguments = optionalId
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
        // Figma "04 Income & freelance invoices" (FR-B1 to FR-B9).
        composable(Routes.INCOME) { entry ->

            if (session !is Session.SignedIn) {
                return@composable
            }

            IncomeScreen(
                onBack = navController.closer(entry),

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

        composable(Routes.SAVINGS_GOAL) { entry ->

            if (session !is Session.SignedIn) {
                return@composable
            }

            val goalViewModel: GoalViewModel = hiltViewModel()

            SavingsGoalScreen(
                viewModel = goalViewModel,
                onBack = navController.closer(entry)
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

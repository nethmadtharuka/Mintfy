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

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val ADD_EXPENSE = "add_expense"
    const val ADD_INCOME = "add_income"
    const val HISTORY = "history"

    /** Optional entry ID: with it, the add screens edit that saved entry instead (FR-C5). */
    const val ARG_ID = "id"

    fun editExpense(id: String) = "$ADD_EXPENSE?$ARG_ID=${Uri.encode(id)}"
    fun editIncome(id: String) = "$ADD_INCOME?$ARG_ID=${Uri.encode(id)}"
}

private val optionalId = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType; nullable = true })

private val authRoutes = setOf(Routes.LOGIN, Routes.REGISTER)

@Composable
fun MintfyNavHost(
    // Called outside any destination, so it is scoped to the activity and outlives navigation.
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val session by authViewModel.session.collectAsStateWithLifecycle()

    if (session == Session.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val navController = rememberNavController()
    val startDestination = remember { if (session is Session.SignedIn) Routes.DASHBOARD else Routes.LOGIN }

    // Follow sign-in and sign-out wherever they happen. Clearing the back stack means the
    // dashboard can't be reached with Back once signed out, and the login screen can't once signed in.
    LaunchedEffect(session) {
        val route = navController.currentDestination?.route
        when {
            session is Session.SignedIn && route in authRoutes -> navController.replaceAll(Routes.DASHBOARD)
            session == Session.SignedOut && route !in authRoutes -> navController.replaceAll(Routes.LOGIN)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onRegisterClick = {
                    authViewModel.clearErrors()
                    navController.navigate(Routes.REGISTER) { launchSingleTop = true }
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
            // Never render signed-in content without a session, even for the frame before the redirect.
            val signedIn = session as? Session.SignedIn ?: return@composable
            DashboardScreen(
                email = signedIn.email,
                onAddExpense = { navController.navigate(Routes.ADD_EXPENSE) { launchSingleTop = true } },
                onAddIncome = { navController.navigate(Routes.ADD_INCOME) { launchSingleTop = true } },
                onHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
                onSignOut = authViewModel::signOut,
            )
        }
        composable(Routes.HISTORY) { entry ->
            if (session !is Session.SignedIn) return@composable
            HistoryScreen(
                onBack = navController.closer(entry),
                onOpenEntry = { row ->
                    val route = when (row.kind) {
                        EntryKind.EXPENSE -> Routes.editExpense(row.id)
                        EntryKind.INCOME -> Routes.editIncome(row.id)
                    }
                    navController.navigate(route) { launchSingleTop = true }
                },
                onAddExpense = { navController.navigate(Routes.ADD_EXPENSE) { launchSingleTop = true } },
                onAddIncome = { navController.navigate(Routes.ADD_INCOME) { launchSingleTop = true } },
            )
        }
        composable("${Routes.ADD_EXPENSE}?${Routes.ARG_ID}={${Routes.ARG_ID}}", arguments = optionalId) { entry ->
            if (session !is Session.SignedIn) return@composable
            val close = navController.closer(entry)
            AddExpenseScreen(onBack = close, onSaved = close)
        }
        composable("${Routes.ADD_INCOME}?${Routes.ARG_ID}={${Routes.ARG_ID}}", arguments = optionalId) { entry ->
            if (session !is Session.SignedIn) return@composable
            val close = navController.closer(entry)
            AddIncomeScreen(onBack = close, onSaved = close)
        }
    }
}

/**
 * Closes [entry], returning to whichever screen opened it (the dashboard, or History when editing).
 * Does nothing once [entry] is no longer on top, so it is safe if Back and Save both fire,
 * or if onSaved fires again after a rotation while isSaved is still true.
 */
private fun NavHostController.closer(entry: NavBackStackEntry): () -> Unit = {
    if (currentBackStackEntry == entry) popBackStack()
}

private fun NavHostController.replaceAll(route: String) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}

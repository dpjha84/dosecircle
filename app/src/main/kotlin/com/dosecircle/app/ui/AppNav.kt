package com.dosecircle.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dosecircle.app.AppContainer
import com.dosecircle.app.ui.circle.CircleScreen
import com.dosecircle.app.ui.history.HistoryScreen
import com.dosecircle.app.ui.medicines.MedicineEditScreen
import com.dosecircle.app.ui.medicines.MedicinesScreen
import com.dosecircle.app.ui.onboarding.OnboardingScreen
import com.dosecircle.app.ui.paywall.PaywallScreen
import com.dosecircle.app.ui.settings.SettingsScreen
import com.dosecircle.app.ui.theme.DoseCircleTheme
import com.dosecircle.app.ui.today.TodayScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Today", Icons.Rounded.Today),
    Medicines("medicines", "Medicines", Icons.Rounded.MedicalServices),
    Circle("circle", "Circle", Icons.Rounded.Groups),
    History("history", "History", Icons.Rounded.History),
    More("settings", "More", Icons.Rounded.MoreHoriz),
}

private const val ONBOARDING = "onboarding"
private const val MEDICINE = "medicine/{id}"
private const val PAYWALL = "paywall?reason={reason}"

@Composable
fun DoseCircleRoot(container: AppContainer) {
    val settings by container.settings.collectAsStateWithLifecycle()
    DoseCircleTheme(comfort = settings.comfortMode) {
        val nav = rememberNavController()
        // The start destination is fixed for the life of the NavHost; later changes navigate explicitly.
        val start = remember { if (container.settings.value.onboardingDone) Tab.Today.route else ONBOARDING }

        // "Erase everything" flips onboardingDone off: bring the person back to the beginning.
        LaunchedEffect(settings.onboardingDone) {
            val current = nav.currentBackStackEntry?.destination?.route
            if (!settings.onboardingDone && current != null && current != ONBOARDING) {
                nav.navigate(ONBOARDING) { popUpTo(nav.graph.id) { inclusive = true } }
            }
        }

        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val showBar = Tab.values().any { it.route == route }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.goTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {
                composable(ONBOARDING) {
                    OnboardingScreen(onFinished = { nav.navigate(Tab.Today.route) { popUpTo(ONBOARDING) { inclusive = true } } })
                }
                composable(Tab.Today.route) { TodayScreen(onAddMedicine = { nav.navigate("medicine/new") }) }
                composable(Tab.Medicines.route) { MedicinesScreen(onOpen = { nav.navigate("medicine/$it") }, onAdd = { nav.navigate("medicine/new") }) }
                composable(Tab.Circle.route) { CircleScreen(onPaywall = nav::paywall) }
                composable(Tab.History.route) { HistoryScreen(onPaywall = nav::paywall) }
                composable(Tab.More.route) { SettingsScreen(onPaywall = nav::paywall) }
                composable(MEDICINE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                    val id = e.arguments?.getString("id")?.takeIf { it != "new" }
                    MedicineEditScreen(medId = id, onDone = { nav.popBackStack() }, onPaywall = nav::paywall)
                }
                composable(PAYWALL, arguments = listOf(navArgument("reason") { type = NavType.StringType; defaultValue = "" })) { e ->
                    PaywallScreen(reason = e.arguments?.getString("reason").orEmpty(), onClose = { nav.popBackStack() })
                }
            }
        }
    }
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(Tab.Today.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.paywall(reason: String) {
    navigate("paywall?reason=${Uri.encode(reason)}") { launchSingleTop = true }
}

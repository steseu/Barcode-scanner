package com.barcodebridge.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.barcodebridge.app.R
import com.barcodebridge.app.ui.history.HistoryScreen
import com.barcodebridge.app.ui.scan.ScanScreen
import com.barcodebridge.app.ui.settings.SettingsScreen

private sealed class Destination(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Scan : Destination("scan", R.string.nav_scan, Icons.Filled.QrCodeScanner)
    data object History : Destination("history", R.string.nav_history, Icons.Filled.History)
    data object Settings : Destination("settings", R.string.nav_settings, Icons.Filled.Settings)
}

private val bottomNavDestinations = listOf(Destination.Scan, Destination.History, Destination.Settings)

@Composable
fun BarcodeBridgeApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                bottomNavDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Scan.route,
            modifier = androidx.compose.ui.Modifier.padding(padding),
        ) {
            composable(Destination.Scan.route) { ScanScreen() }
            composable(Destination.History.route) { HistoryScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}

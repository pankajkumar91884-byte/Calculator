package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.database.VaultDatabase
import com.example.data.repository.VaultRepository
import com.example.ui.calculator.CalculatorScreen
import com.example.ui.calculator.CalculatorViewModel
import com.example.ui.theme.CalculatorVaultTheme
import com.example.ui.vault.FolderContentsScreen
import com.example.ui.vault.SettingsScreen
import com.example.ui.vault.VaultDashboardScreen
import com.example.ui.vault.VaultViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : ComponentActivity() {

    private lateinit var repository: VaultRepository
    private lateinit var calculatorViewModel: CalculatorViewModel
    private lateinit var vaultViewModel: VaultViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize DB and Repository layers (no heavy DI wrappers)
        val database = VaultDatabase.getDatabase(applicationContext)
        repository = VaultRepository(database.vaultDao())

        // 2. Instantiate ViewModels directly
        calculatorViewModel = CalculatorViewModel(repository)
        vaultViewModel = VaultViewModel(repository)

        // 3. Security Monitor: Screenshot prevention flag based on settings flow
        repository.settingsFlow
            .onEach { settings ->
                val prevent = settings?.preventScreenshots ?: false
                if (prevent) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            .launchIn(lifecycleScope)

        setContent {
            // Collect theme configurations dynamically
            val settings by repository.settingsFlow.collectAsState(initial = null)
            val useDarkMode = settings?.useDarkMode ?: true

            CalculatorVaultTheme(darkTheme = useDarkMode) {
                val navController = rememberNavController()
                
                // Track internal unlock state to force safe lock routing
                val isUnlocked by vaultViewModel.isVaultUnlocked.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "calculator",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Standard working calculator entry point
                        composable("calculator") {
                            CalculatorScreen(
                                viewModel = calculatorViewModel,
                                onNavigateToVault = {
                                    navController.navigate("vault_dashboard") {
                                        popUpTo("calculator") { inclusive = false }
                                    }
                                }
                            )
                        }

                        // Hidden Secure Vault Dashboard
                        composable("vault_dashboard") {
                            // If lock triggered globally, redirect quickly to calc
                            if (!isUnlocked) {
                                LaunchedEffect(Unit) {
                                    navController.navigate("calculator") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }

                            VaultDashboardScreen(
                                viewModel = vaultViewModel,
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToFolder = { folderId ->
                                    navController.navigate("folder_detail/$folderId")
                                }
                            )
                        }

                        // Directory nested lists view
                        composable(
                            route = "folder_detail/{folderId}",
                            arguments = listOf(navArgument("folderId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val folderId = backStackEntry.arguments?.getInt("folderId") ?: 0
                            
                            if (!isUnlocked) {
                                LaunchedEffect(Unit) {
                                    navController.navigate("calculator") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }

                            FolderContentsScreen(
                                viewModel = vaultViewModel,
                                folderId = folderId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Settings and security configuration panel
                        composable("settings") {
                            if (!isUnlocked) {
                                LaunchedEffect(Unit) {
                                    navController.navigate("calculator") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }

                            SettingsScreen(
                                viewModel = vaultViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 4. Secure Auto-Lock on Backgrounding:
        // Whenever the user exits the app (presses home, matches calls, locks screen),
        // we instantly clear active key variables from memory and lock the vault.
        repository.lockVault()
    }
}

package com.example.ui.vault

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.StorageManager
import com.example.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: VaultViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsState()

    // PIN change dialog controllers
    var showPinChangeDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("VAULT SETTINGS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_settings_back")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Security Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Security", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    // Toggle screenshots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Screenshot Protection", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Disallow screenshot captures inside secure vault folders.", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = settings?.preventScreenshots ?: false,
                            onCheckedChange = { viewModel.toggleScreenshotPrevention(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("toggle_screenshots")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Change PIN button launcher
                    Button(
                        onClick = { showPinChangeDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_change_pin")
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Access PIN", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Section 2: Display Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Theming", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dark Theme", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Toggle light/dark appearance for the calculator and vault.", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = settings?.useDarkMode ?: true,
                            onCheckedChange = { viewModel.toggleDarkMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("toggle_dark_theme")
                        )
                    }
                }
            }

            // Section 3: System Device Space stats
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Device Storage", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    val freeSpace = remember { StorageManager.getFreeSpaceBytes(context) }
                    val totalSpace = remember { StorageManager.getTotalSpaceBytes(context) }
                    val sizeFormatted = remember { StorageManager.formatFileSize(totalSpace - freeSpace) }
                    val freeFormatted = remember { StorageManager.formatFileSize(freeSpace) }
                    val totalFormatted = remember { StorageManager.formatFileSize(totalSpace) }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Available Storage Space:", fontSize = 13.sp)
                            Text(freeFormatted, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Used System Space:", fontSize = 13.sp)
                            Text(sizeFormatted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Storage partitions:", fontSize = 13.sp)
                            Text(totalFormatted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // PIN change Dialog form
    if (showPinChangeDialog) {
        var oldPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPinChangeDialog = false },
            title = { Text("Update Private PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordTransformationCheck(),
                        modifier = Modifier.fillMaxWidth().testTag("input_old_pin"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        label = { Text("New PIN (4-8 digits)") },
                        visualTransformation = PasswordTransformationCheck(),
                        modifier = Modifier.fillMaxWidth().testTag("input_new_pin"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { confirmPin = it },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = PasswordTransformationCheck(),
                        modifier = Modifier.fillMaxWidth().testTag("input_confirm_pin"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin != confirmPin) {
                            Toast.makeText(context, "New PIN confirmation does not match", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newPin.length !in 4..8 || !newPin.all { it.isDigit() }) {
                            Toast.makeText(context, "New PIN must be 4 to 8 digits.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.changeSecurePin(oldPin, newPin) { success ->
                            if (success) {
                                showPinChangeDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_pin_confirm")
                ) {
                    Text("Update Close")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinChangeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Custom clear-code safe helper
fun PasswordTransformationCheck(): PasswordVisualTransformation {
    return PasswordVisualTransformation()
}

package com.example.readmymi.ui.settings

import android.database.sqlite.SQLiteException
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.readmymi.PrefsManager
import com.example.readmymi.data.SensorDatabase
import com.example.readmymi.ui.DropdownSelector
import com.example.readmymi.ui.SettingsCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


/** Data-management card: pruning selector, size display, clear-database dialog. */
@Composable
fun DataSettingsCard(prefs: PrefsManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { SensorDatabase.getDatabase(context) }
    var autoPruningDays by remember { mutableStateOf(prefs.autoPruningDays) }
    var showClearDatabaseDialog by remember { mutableStateOf(false) }
    var databaseSize by remember { mutableStateOf("Calculating...") }

    LaunchedEffect(Unit) {
        databaseSize = kotlinx.coroutines.withContext(Dispatchers.IO) { getDatabaseSize(context) }
    }

    SettingsCard {
        SettingsSectionHeader("Data Storage", androidx.compose.material.icons.Icons.Default.Storage)

        com.example.readmymi.ui.DropdownSelector(
            label = "Auto-delete Old Data",
            options = listOf(0, 7, 30, 90, 180, 365),
            selectedOption = autoPruningDays,
            optionToString = { days ->
                when (days) {
                    0 -> "Never"; 7 -> "Older than 7 days"; 30 -> "Older than 30 days"
                    90 -> "Older than 90 days"; 180 -> "Older than 6 months"
                    365 -> "Older than 1 year"; else -> "$days days"
                }
            },
            onOptionSelected = { days -> autoPruningDays = days; prefs.autoPruningDays = days }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Database Size", style = MaterialTheme.typography.bodyLarge)
                Text(
                    databaseSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showClearDatabaseDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear database")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Database")
            }
        }
    }

    if (showClearDatabaseDialog) {
        AlertDialog(
            onDismissRequest = { showClearDatabaseDialog = false },
            title = { Text("Clear Database") },
            text = { Text("Are you sure you want to delete all sensor data? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDatabaseDialog = false
                        scope.launch {
                            try {
                                database.sensorDao().deleteAll()
                                Toast.makeText(context, "Database cleared successfully", Toast.LENGTH_SHORT).show()
                            } catch (e: SQLiteException) {
                                Toast.makeText(context, "Database is locked. Please try again later.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDatabaseDialog = false }) { Text("Cancel") }
            }
        )
    }
}

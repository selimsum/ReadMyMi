package com.example.readmymi.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.readmymi.AppLogger
import com.example.readmymi.PrefsManager
import com.example.readmymi.ui.SettingsCard
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/** Diagnostics card: recent-MAC ring buffer, log viewer, copy/share. */
@Composable
fun DiagnosticsCard(prefs: PrefsManager, lastMac: String) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val logs by AppLogger.logs.collectAsState()
    val recentMacs = remember { mutableStateListOf<String>() }

    LaunchedEffect(lastMac) {
        if (lastMac.isNotEmpty() && !recentMacs.contains(lastMac)) {
            recentMacs.add(0, lastMac)
            if (recentMacs.size > 10) recentMacs.removeAt(recentMacs.size - 1)
        }
    }

    SettingsCard {
        Text("Recent Devices", style = MaterialTheme.typography.titleMedium)
        // Fixed-height box (not a nested LazyColumn: parent is already a LazyColumn,
        // nested scrolling lists warn/fail lint and break touch handling).
        Column(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            recentMacs.take(5).forEach { mac ->
                Text(
                    prefs.getDeviceName(mac),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text("Logs", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Static snapshot of last 100 lines: avoids nested LazyColumn inside
            // the parent LazyColumn (lint: NestedScrollingWidgets).
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                logs.takeLast(10).forEach { log ->
                    Text(log, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy")
            }
            Button(onClick = {
                scope.launch {
                    try {
                        val file = File(context.cacheDir, "readmymi_logs.txt")
                        FileOutputStream(file).use { it.write(logs.joinToString("\n").toByteArray()) }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Logs"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to share logs: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, contentDescription = "Share logs")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }
        }
        if (logs.isNotEmpty()) {
            TextButton(onClick = { AppLogger.clear() }) { Text("Clear Logs") }
        }
    }
}


package com.example.readmymi.ui.settings

import android.content.Context
import java.io.File

fun getDatabaseSize(context: Context): String {
    val dbFile: File? = try { context.getDatabasePath("sensor_database") } catch (e: Exception) { null }
    val parent = dbFile?.parent ?: return "Unknown"
    val walFile = File(parent, "sensor_database-wal")
    val shmFile = File(parent, "sensor_database-shm")
    var totalBytes = 0L
    listOfNotNull(dbFile, walFile.takeIf { it.exists() }, shmFile.takeIf { it.exists() })
        .forEach { totalBytes += it.length() }
    return when {
        totalBytes < 1024 -> "$totalBytes B"
        totalBytes < 1024 * 1024 -> "${totalBytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0))
    }
}

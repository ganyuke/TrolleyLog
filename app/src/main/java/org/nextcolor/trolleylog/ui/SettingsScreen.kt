package org.nextcolor.trolleylog.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.nextcolor.trolleylog.data.Backup
import org.nextcolor.trolleylog.data.Ride
import org.nextcolor.trolleylog.data.RideStore
import java.time.LocalDate

fun rideCount(rides: List<Ride>): String = rideCount(rides.size)

fun rideCount(n: Int): String = if (n == 1) "1 ride" else "$n rides"

/** An import the person asked for, waiting on the backup choice and then the file. */
private enum class PendingImport(val sheet: Boolean, val replace: Boolean) {
    BACKUP(false, false), BACKUP_REPLACE(false, true), SHEET(true, false), SHEET_REPLACE(true, true),
}

private val backupTypes = arrayOf("application/json", "text/plain", "application/octet-stream")
private val sheetTypes = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")

@Composable
fun SettingsScreen(rides: List<Ride>, store: RideStore, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onMessage: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val count = rideCount(rides)

    // Asking about a backup, then picking the file, then importing it.
    var asking by rememberSaveable { mutableStateOf<PendingImport?>(null) }
    var afterBackup by rememberSaveable { mutableStateOf<PendingImport?>(null) }
    var picking by rememberSaveable { mutableStateOf<PendingImport?>(null) }

    val saveSheet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) onMessage(write(context, uri, Backup.toCsv(rides), "Spreadsheet saved with $count."))
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val replace = picking?.replace == true
        picking = null
        if (uri != null) onMessage(
            importFrom(context, uri) { text ->
                val incoming = Backup.fromJson(text)
                if (replace) replaceResult(store.replace(incoming), "backup")
                else importResult(store.import(incoming), "backup")
            }
        )
    }
    val openSheet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val replace = picking?.replace == true
        picking = null
        if (uri != null) onMessage(
            importFrom(context, uri) { text ->
                val sheet = Backup.fromCsv(text)
                val result = if (replace) replaceResult(store.replace(sheet.rides), "spreadsheet")
                else importResult(store.import(sheet.rides), "spreadsheet")
                when (sheet.skipped) {
                    0 -> result
                    1 -> "$result 1 row without a date or boarding time was skipped."
                    else -> "$result ${sheet.skipped} rows without a date or boarding time were skipped."
                }
            }
        )
    }
    fun pick(pending: PendingImport) {
        picking = pending
        if (pending.sheet) openSheet.launch(sheetTypes) else openBackup.launch(backupTypes)
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val next = afterBackup
        afterBackup = null
        if (uri != null) {
            val saved = "Backup saved with $count."
            val message = write(context, uri, Backup.toJson(rides), saved)
            onMessage(message)
            // The import goes on only once the backup is saved.
            if (next != null && message == saved) pick(next)
        }
    }
    val backupName = "trolley-log-${LocalDate.now()}.json"
    fun startImport(pending: PendingImport) {
        if (rides.isEmpty()) pick(pending) else asking = pending
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Backup") {
                SegmentedList {
                    Segment(
                        0, 3, "Save a backup", Icons.Rounded.Save,
                        onClick = { saveBackup.launch(backupName) },
                        supporting = "All $count in one file.",
                        enabled = rides.isNotEmpty(),
                    )
                    Segment(
                        1, 3, "Import a backup", Icons.Rounded.FileOpen,
                        onClick = { startImport(PendingImport.BACKUP) },
                        supporting = "Adds rides from a backup file. Rides already here are not changed.",
                    )
                    Segment(
                        2, 3, "Import a backup and replace", Icons.Rounded.FindReplace,
                        onClick = { startImport(PendingImport.BACKUP_REPLACE) },
                        supporting = "Adds rides from a backup file and replaces rides already here with the ones in the file.",
                    )
                }
            }
            Section("Spreadsheet") {
                SegmentedList {
                    Segment(
                        0, 3, "Export a spreadsheet", Icons.Rounded.TableChart,
                        onClick = { saveSheet.launch("trolley-log-${LocalDate.now()}.csv") },
                        supporting = "A CSV file for a spreadsheet app.",
                        enabled = rides.isNotEmpty(),
                    )
                    Segment(
                        1, 3, "Import a spreadsheet", Icons.Rounded.FileOpen,
                        onClick = { startImport(PendingImport.SHEET) },
                        supporting = "Adds rides from a CSV file. Rides already here are not changed.",
                    )
                    Segment(
                        2, 3, "Import a spreadsheet and replace", Icons.Rounded.FindReplace,
                        onClick = { startImport(PendingImport.SHEET_REPLACE) },
                        supporting = "Adds rides from a CSV file and replaces rides already here with the ones in the file.",
                    )
                }
            }
            Text(
                "Map © OpenStreetMap contributors. Stations and tracks from MTS.",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    asking?.let { pending ->
        BackupFirstDialog(
            onBackup = {
                asking = null
                afterBackup = pending
                saveBackup.launch(backupName)
            },
            onSkip = {
                asking = null
                pick(pending)
            },
            onCancel = { asking = null },
        )
    }
}

@Composable
private fun BackupFirstDialog(onBackup: () -> Unit, onSkip: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Save a backup first?") },
        confirmButton = { TextButton(onClick = onBackup) { Text("Save a backup") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Skip") } },
    )
}

/** Reads the file as text and hands it to [read], which returns the message to show. */
private fun importFrom(context: Context, uri: Uri, read: (String) -> String): String = try {
    read(context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })
} catch (e: Backup.FormatException) {
    e.message!!
} catch (e: Exception) {
    "That file could not be opened."
}

private fun importResult(result: RideStore.ImportResult, kind: String): String = when {
    result.added == 0 && result.alreadyHere == 0 -> "That $kind has no rides in it."
    result.added == 0 -> "Nothing to add. All ${result.alreadyHere} rides are already here."
    result.alreadyHere == 0 -> "Added ${result.added} rides."
    else -> "Added ${result.added} rides. ${result.alreadyHere} were already here."
}

private fun replaceResult(result: RideStore.ReplaceResult, kind: String): String {
    val done = listOfNotNull(
        "Added ${rideCount(result.added)}.".takeIf { result.added > 0 },
        "Replaced ${rideCount(result.replaced)}.".takeIf { result.replaced > 0 },
        when (result.unchanged) {
            0 -> null
            1 -> "1 was already the same."
            else -> "${result.unchanged} were already the same."
        }.takeIf { result.added + result.replaced > 0 },
    )
    return when {
        done.isNotEmpty() -> done.joinToString(" ")
        result.unchanged == 0 -> "That $kind has no rides in it."
        else -> "Nothing to change. All ${result.unchanged} rides are already the same."
    }
}

private fun write(context: Context, uri: Uri, text: String, done: String): String = try {
    // "wt" truncates, so saving over a longer file does not leave old bytes behind.
    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    done
} catch (e: Exception) {
    "The file could not be saved."
}

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.FileOpen
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

fun rideCount(rides: List<Ride>): String = if (rides.size == 1) "1 ride" else "${rides.size} rides"

@Composable
fun SettingsScreen(rides: List<Ride>, store: RideStore, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onMessage: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val count = rideCount(rides)

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onMessage(write(context, uri, Backup.toJson(rides), "Backup saved with $count."))
    }
    val saveSheet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) onMessage(write(context, uri, Backup.toCsv(rides), "Spreadsheet saved with $count."))
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onMessage(
            importFrom(context, uri) { text -> importResult(store.import(Backup.fromJson(text)), "backup") }
        )
    }
    val openSheet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onMessage(
            importFrom(context, uri) { text ->
                val sheet = Backup.fromCsv(text)
                val result = importResult(store.import(sheet.rides), "spreadsheet")
                when (sheet.skipped) {
                    0 -> result
                    1 -> "$result 1 row without a date or boarding time was skipped."
                    else -> "$result ${sheet.skipped} rows without a date or boarding time were skipped."
                }
            }
        )
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
                        0, 2, "Save a backup", Icons.Rounded.Save,
                        onClick = { saveBackup.launch("trolley-log-${LocalDate.now()}.json") },
                        supporting = "All $count in one file.",
                        enabled = rides.isNotEmpty(),
                    )
                    Segment(
                        1, 2, "Import a backup", Icons.Rounded.FileOpen,
                        onClick = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        supporting = "Adds rides from a backup file. Rides already here are not changed.",
                    )
                }
            }
            Section("Spreadsheet") {
                SegmentedList {
                    Segment(
                        0, 2, "Export a spreadsheet", Icons.Rounded.TableChart,
                        onClick = { saveSheet.launch("trolley-log-${LocalDate.now()}.csv") },
                        supporting = "A CSV file for a spreadsheet app.",
                        enabled = rides.isNotEmpty(),
                    )
                    Segment(
                        1, 2, "Import a spreadsheet", Icons.Rounded.FileOpen,
                        onClick = { openSheet.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) },
                        supporting = "Adds rides from a CSV file. Rides already here are not changed.",
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

private fun write(context: Context, uri: Uri, text: String, done: String): String = try {
    // "wt" truncates, so saving over a longer file does not leave old bytes behind.
    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    done
} catch (e: Exception) {
    "The file could not be saved."
}

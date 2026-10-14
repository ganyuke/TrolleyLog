package org.nextcolor.trolleylog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.nextcolor.trolleylog.data.Network

/** Full-screen station chooser. Mark a station on the map or in the list, then say what it is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationPicker(
    boardStation: String?,
    exitStation: String?,
    onBoard: (String?) -> Unit,
    onExit: (String?) -> Unit,
    onClose: () -> Unit,
    /** Likely stations, listed first in the list view. */
    suggested: List<String> = emptyList(),
) {
    var marked by rememberSaveable { mutableStateOf<String?>(null) }
    var asList by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text("Stations") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalIconToggleButton(checked = asList, onCheckedChange = { asList = it }, Modifier.padding(end = 8.dp)) {
                        Icon(
                            if (asList) Icons.Rounded.Map else Icons.AutoMirrored.Rounded.List,
                            contentDescription = if (asList) "Show the map" else "Show the list",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceBright,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
                    val station = Network.station(marked)
                    if (station == null) {
                        TripLabel(
                            boardStation, exitStation,
                            style = MaterialTheme.typography.titleMedium,
                            empty = "Tap a station on the map.",
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(station.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            LineDots(Network.linesAt(station.id).map { it.color })
                        }
                        Spacer(Modifier.size(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val boardsHere = station.id == boardStation
                            val exitsHere = station.id == exitStation
                            ChoiceButton("Board here", Icons.Rounded.TripOrigin, boardsHere, Modifier.weight(1f)) {
                                onBoard(if (boardsHere) null else station.id)
                            }
                            ChoiceButton("Disembark here", Icons.Rounded.Place, exitsHere, Modifier.weight(1f)) {
                                onExit(if (exitsHere) null else station.id)
                            }
                        }
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            if (asList) {
                LazyColumn(Modifier.fillMaxSize()) {
                    val row: @Composable (String) -> Unit = { id ->
                        val tag = when (id) {
                            boardStation -> "Board"
                            exitStation -> "Disembark"
                            else -> null
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { marked = id }
                                .background(if (id == marked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(Network.stationName(id).orEmpty(), style = MaterialTheme.typography.bodyLarge)
                            if (tag != null) Text(tag, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    val header: @Composable (String, Long?) -> Unit = { title, color ->
                        Row(Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (color != null) {
                                LineDots(listOf(color))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (suggested.isNotEmpty()) {
                        item(key = "suggested") { header("Suggested", null) }
                        items(suggested, key = { "suggested$it" }) { row(it) }
                    }
                    for (line in Network.lines) {
                        item(key = line.id) { header(line.name, line.color) }
                        items(line.stationIds, key = { line.id + it }) { row(it) }
                    }
                }
            } else {
                StationMap(boardStation, exitStation, marked, onMark = { marked = it })
            }
        }
    }
}

/** Stays filled with a tick while the marked station holds that role. Pressing again takes it off. */
@Composable
private fun ChoiceButton(text: String, icon: ImageVector, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    ToggleButton(checked = chosen, onCheckedChange = { onClick() }, modifier = modifier) {
        Icon(if (chosen) Icons.Rounded.Check else icon, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text)
    }
}

fun tripText(board: String?, exit: String?): String? {
    val a = Network.stationName(board)
    val b = Network.stationName(exit)
    return when {
        a != null && b != null -> "$a to $b"
        a != null -> "From $a"
        b != null -> "To $b"
        else -> null
    }
}

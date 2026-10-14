package org.nextcolor.trolleylog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.nextcolor.trolleylog.Draft
import org.nextcolor.trolleylog.data.CarId
import org.nextcolor.trolleylog.data.Network
import org.nextcolor.trolleylog.data.Ride
import org.nextcolor.trolleylog.logic.Suggestions
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Stations from past rides, most used first. */
private fun usedStations(rides: List<Ride>): List<String> =
    rides.flatMap { listOfNotNull(it.boardStation, it.exitStation) }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditRideScreen(
    draft: Draft,
    allRides: List<Ride>,
    onChange: (Ride) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val ride = draft.ride
    var pickingStations by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun setStations(board: String?, exit: String?) {
        val candidates = Network.linesBetween(board, exit).map { it.id }
        val line = when {
            candidates.size == 1 -> candidates[0]
            ride.line in candidates -> ride.line
            else -> Suggestions.usualLine(allRides, board, exit)
        }
        onChange(ride.copy(boardStation = board, exitStation = exit, line = line))
    }

    if (pickingStations) {
        BackHandler { pickingStations = false }
        StationPicker(
            boardStation = ride.boardStation,
            exitStation = ride.exitStation,
            onBoard = { setStations(it, ride.exitStation) },
            onExit = { setStations(ride.boardStation, it) },
            onClose = { pickingStations = false },
            suggested = (draft.trips.flatMap { listOf(it.first, it.second) } + usedStations(allRides)).distinct().take(6),
        )
        return
    }
    BackHandler(onBack = onCancel)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(if (draft.isNew) "New ride" else "Edit ride") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "Cancel") }
                },
                actions = {
                    Button(onClick = onSave, Modifier.padding(end = 12.dp), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TripSection(
                ride, draft.trips,
                onPick = { pickingStations = true },
                onTrip = { (board, exit) -> setStations(board, exit) },
                onLine = { onChange(ride.copy(line = it)) },
            )
            CarSection(ride, onChange)
            TimeSection(ride, onChange)
            BrochureSection(ride, allRides, onChange)
            Section("Note") {
                Panel {
                    OutlinedTextField(
                        value = ride.note,
                        onValueChange = { onChange(ride.copy(note = it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (!draft.isNew) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Delete ride")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this ride?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TripSection(
    ride: Ride,
    trips: List<Pair<String, String>>,
    onPick: () -> Unit,
    onTrip: (Pair<String, String>) -> Unit,
    onLine: (String) -> Unit,
) {
    Section("Stations") {
        // Likely trips first, so the common case is one tap or none.
        if (trips.isNotEmpty()) {
            FlowRow(
                Modifier.padding(start = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (trip in trips) {
                    val chosen = trip == ride.boardStation to ride.exitStation
                    FilterChip(
                        selected = chosen,
                        onClick = { onTrip(trip) },
                        label = { TripLabel(trip.first, trip.second, arrowSize = 16.dp) },
                        leadingIcon = if (chosen) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null,
                    )
                }
            }
        }
        SegmentedList {
            Segment(
                0, 2, "Board", Icons.Rounded.TripOrigin,
                onClick = onPick, supporting = Network.stationName(ride.boardStation) ?: "No station",
                trailing = { ride.boardStation?.let { id -> LineDots(Network.linesAt(id).map { it.color }) } },
            )
            Segment(
                1, 2, "Disembark", Icons.Rounded.Place,
                onClick = onPick, supporting = Network.stationName(ride.exitStation) ?: "No station",
                trailing = { ride.exitStation?.let { id -> LineDots(Network.linesAt(id).map { it.color }) } },
            )
        }
        // Only asked where two lines share the track between the chosen stations.
        val shared = Network.linesBetween(ride.boardStation, ride.exitStation)
        if (shared.size > 1) {
            Spacer(Modifier.height(12.dp))
            Panel {
                Text("Line", style = MaterialTheme.typography.titleSmall)
                ChoiceGroup(shared.map { it.id }, ride.line, onLine, Modifier.fillMaxWidth()) { id ->
                    val line = Network.line(id)!!
                    LineDots(listOf(line.color))
                    Spacer(Modifier.width(8.dp))
                    Text(line.name)
                }
            }
        }
    }
}

@Composable
private fun CarSection(ride: Ride, onChange: (Ride) -> Unit) {
    val carId = ride.carId
    Section("Car") {
        Panel {
            OutlinedTextField(
                value = ride.car,
                onValueChange = { text ->
                    onChange(ride.copy(car = text.uppercase().filter { it.isDigit() || it == 'A' || it == 'B' }.take(5)))
                },
                label = { Text("Car number") },
                placeholder = { Text("4029B") },
                supportingText = {
                    val series = carId?.series
                    Text(
                        when {
                            series != null -> "${series.label}, ${series.lengthFeet} ft, built ${series.built}"
                            carId != null -> "Not a Trolley car number"
                            else -> ""
                        }
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            CarDetail(
                shape = carId?.series?.shape,
                end = carId?.end,
                onSelectEnd = { end ->
                    val digits = ride.car.filter(Char::isDigit)
                    if (digits.isEmpty()) return@CarDetail
                    // Tapping the marked end again takes the letter off.
                    onChange(ride.copy(car = if (carId?.end == end) digits else digits + end))
                },
            )
        }
        Spacer(Modifier.height(ListItemDefaults.SegmentedGap * 4))
        Panel {
            Text("Train length", style = MaterialTheme.typography.titleSmall)
            ChoiceGroup(
                (1..Ride.MAX_TRAIN_LENGTH).toList(), ride.trainLength,
                onSelect = { n -> onChange(ride.copy(trainLength = n, position = ride.position?.takeIf { it <= n })) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("$it") }
            Text("Your car", style = MaterialTheme.typography.titleSmall)
            TrainStrip(
                length = ride.trainLength,
                selected = ride.position,
                shape = carId?.series?.shape,
                onSelect = { onChange(ride.copy(position = if (ride.position == it) null else it)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSection(ride: Ride, onChange: (Ride) -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }
    Section("Time") {
        SegmentedList {
            Segment(0, 3, "Date", Icons.Rounded.CalendarToday, onClick = { picking = "date" }, supporting = ride.date.format(dayFormat))
            Segment(1, 3, "Boarded", Icons.Rounded.Schedule, onClick = { picking = "boarded" }, supporting = ride.boardedAt.clock())
            Segment(
                2, 3, "Arrived", Icons.Rounded.Flag, onClick = { picking = "arrived" },
                supporting = listOfNotNull(ride.arrivedAt?.clock() ?: "Not yet", ride.minutes?.let(::minutesText)).joinToString(" · "),
                trailing = {
                    if (ride.arrivedAt == null) {
                        FilledTonalButton(onClick = { onChange(ride.copy(arrivedAt = LocalTime.now().truncatedTo(ChronoUnit.MINUTES))) }) {
                            Text("Now")
                        }
                    } else {
                        IconButton(onClick = { onChange(ride.copy(arrivedAt = null)) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear arrival time")
                        }
                    }
                },
            )
        }
    }

    when (picking) {
        "date" -> {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = ride.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            onChange(ride.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
                        }
                        picking = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
            ) { DatePicker(state) }
        }
        "boarded", "arrived" -> {
            val boarded = picking == "boarded"
            val start = (if (boarded) ride.boardedAt else ride.arrivedAt) ?: LocalTime.now()
            val state = rememberTimePickerState(start.hour, start.minute)
            TimePickerDialog(
                onDismissRequest = { picking = null },
                title = { Text(if (boarded) "Boarded" else "Arrived") },
                confirmButton = {
                    TextButton(onClick = {
                        val t = LocalTime.of(state.hour, state.minute)
                        onChange(if (boarded) ride.copy(boardedAt = t) else ride.copy(arrivedAt = t))
                        picking = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
            ) { TimePicker(state) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrochureSection(ride: Ride, allRides: List<Ride>, onChange: (Ride) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    // Brochures picked up before, newest first, so a repeat is one tap.
    val known = remember(allRides) {
        allRides.sortedWith(compareBy({ it.date }, { it.boardedAt })).reversed().flatMap { it.brochures }.distinct()
    }
    fun add() {
        val name = typed.trim()
        if (name.isNotEmpty() && name !in ride.brochures) onChange(ride.copy(brochures = ride.brochures + name))
        typed = ""
    }
    Section("Brochures") {
        Panel {
            val chips = (ride.brochures + known).distinct()
            if (chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (name in chips) {
                        val has = name in ride.brochures
                        FilterChip(
                            selected = has,
                            onClick = { onChange(ride.copy(brochures = if (has) ride.brochures - name else ride.brochures + name)) },
                            label = { Text(name) },
                            leadingIcon = if (has) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("Add a brochure") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = ::add, enabled = typed.isNotBlank()) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add brochure")
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

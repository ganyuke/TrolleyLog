package org.nextcolor.trolleylog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nextcolor.trolleylog.data.Network
import org.nextcolor.trolleylog.data.Ride
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val longDay = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val longDayYear = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp)

@Composable
fun TodayScreen(rides: List<Ride>, today: LocalDate, onEdit: (Ride) -> Unit, onArrived: (Ride) -> Unit, modifier: Modifier = Modifier) {
    val todays = rides.filter { it.date == today }
    if (todays.isEmpty()) {
        EmptyMessage(Icons.Rounded.Tram, "No rides today", "Tap Board when you board.")
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(todays, key = { it.id }) { ride ->
            RideCard(ride, onClick = { onEdit(ride) }, onArrived = { onArrived(ride) }.takeIf { ride.arrivedAt == null })
        }
    }
}

@Composable
fun HistoryScreen(rides: List<Ride>, onEdit: (Ride) -> Unit, modifier: Modifier = Modifier) {
    if (rides.isEmpty()) {
        EmptyMessage(Icons.Rounded.History, "No rides yet", "Your rides will show up here.")
        return
    }
    val days = rides.groupBy { it.date }.toSortedMap(reverseOrder())
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        for ((day, list) in days) {
            item(key = day.toString()) {
                Text(
                    day.format(if (day.year == LocalDate.now().year) longDay else longDayYear),
                    Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            itemsIndexed(list, key = { _, ride -> ride.id }) { i, ride -> RideRow(ride, i, list.size, onClick = { onEdit(ride) }) }
        }
    }
}

/** One ride as a compact list row: trip and times on the left, car on the right. */
@Composable
private fun RideRow(ride: Ride, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        supportingContent = {
            val times = ride.boardedAt.clock() + (ride.arrivedAt?.let { " to ${it.clock()}" } ?: "")
            Text(listOfNotNull(times, ride.minutes?.let(::minutesText)).joinToString(" · "))
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                if (ride.car.isNotEmpty()) {
                    Text(ride.car, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                ride.position?.let { Text(carName(it, ride.trainLength) + " car", style = MaterialTheme.typography.labelMedium) }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Network.line(ride.line)?.let { LineDots(listOf(it.color)) }
            TripLabel(ride.boardStation, ride.exitStation)
        }
    }
}

fun todayTitle(today: LocalDate): String = today.format(longDay)

fun daySummary(rides: List<Ride>): String {
    if (rides.isEmpty()) return "No rides yet"
    val count = if (rides.size == 1) "1 ride" else "${rides.size} rides"
    val minutes = rides.sumOf { it.minutes ?: 0 }
    return if (minutes > 0) "$count, ${minutesText(minutes)} on board" else count
}

/**
 * Today's ride as a card: the train drawing up top, then the trip, and a footer with the car and, while still riding, the arrival button.
 */
@Composable
fun RideCard(ride: Ride, onClick: () -> Unit, onArrived: (() -> Unit)?) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            TrainStrip(ride.trainLength, ride.position, ride.carId?.series?.shape, showLabels = false)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Network.line(ride.line)?.let { LineDots(listOf(it.color)) }
                TripLabel(ride.boardStation, ride.exitStation, style = MaterialTheme.typography.titleLarge, arrowSize = 22.dp)
            }
            val times = ride.boardedAt.clock() + (ride.arrivedAt?.let { " to ${it.clock()}" } ?: "")
            Text(
                listOfNotNull(times, ride.minutes?.let(::minutesText)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val car = listOfNotNull(
                        ride.car.takeIf { it.isNotEmpty() },
                        ride.position?.let { carName(it, ride.trainLength).lowercase() + " car" },
                    ).joinToString(", ")
                    if (car.isNotEmpty()) Text(car.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleSmall)
                    val extras = listOfNotNull(
                        ride.carId?.series?.label,
                        ride.brochures.takeIf { it.isNotEmpty() }?.joinToString(", "),
                        ride.note.takeIf { it.isNotEmpty() },
                    ).joinToString(" · ")
                    if (extras.isNotEmpty()) {
                        Text(extras, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onArrived != null) {
                    FilledTonalButton(onClick = onArrived, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(Icons.Rounded.Flag, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Arrived")
                    }
                }
            }
        }
    }
}

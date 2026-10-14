package org.nextcolor.trolleylog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nextcolor.trolleylog.data.CarSeries
import org.nextcolor.trolleylog.data.Network
import org.nextcolor.trolleylog.data.Ride
import org.nextcolor.trolleylog.logic.Stats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val MILES_PER_KM = 0.621371
private val shortDay = DateTimeFormatter.ofPattern("MMM d, yyyy")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(rides: List<Ride>, today: LocalDate, modifier: Modifier = Modifier) {
    if (rides.isEmpty()) {
        EmptyMessage(Icons.Rounded.Insights, "No statistics yet", "Log a ride to get started.")
        return
    }
    val s = remember(rides, today) { Stats.of(rides, today) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            val tile = Modifier.weight(1f)
            // The SD100s have left service, so they only count once one has been logged.
            val fleetSize = CarSeries.entries
                .filter { it != CarSeries.SD100 || it in s.carsRidden }
                .sumOf { it.numbers.count() }
            Tile("Rides", "${s.rides}", "on ${s.days} ${if (s.days == 1) "day" else "days"}", tile)
            Tile("Day streak", "${s.currentStreak}", "best ${s.longestStreak}", tile)
            Tile("Time on board", hours(s.minutesOnBoard), "over ${s.timedRides} rides", tile)
            Tile("Distance", "${(s.km * MILES_PER_KM).roundToInt()} mi", "by rail", tile)
            Tile("Stations", "${s.stationsVisited}", "of ${Network.stations.size}", tile)
            Tile("Cars ridden", "${s.carsRidden.values.sumOf { it.size }}", "of $fleetSize", tile)
        }

        StatSection("Fleet") {
            Text(
                "Cars you have ridden are filled in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (series in CarSeries.entries) {
                val ridden = s.carsRidden[series].orEmpty()
                if (series == CarSeries.SD100 && ridden.isEmpty()) continue
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${series.label}  ${series.numbers.first}–${series.numbers.last}", style = MaterialTheme.typography.labelLarge)
                    Text("${ridden.size} of ${series.numbers.count()}", style = MaterialTheme.typography.labelLarge)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (n in series.numbers) {
                        val cell = Modifier.size(width = 16.dp, height = 10.dp).clip(RoundedCornerShape(2.dp))
                        if (n in ridden) Box(cell.background(MaterialTheme.colorScheme.primary))
                        else Box(cell.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }

        if (s.positions.isNotEmpty()) StatSection("Car position") {
            for ((length, counts) in s.positions.toSortedMap()) {
                Text(
                    if (length == 1) "Single car" else "$length-car trains",
                    style = MaterialTheme.typography.labelLarge,
                )
                TrainStrip(length, selected = null, shape = null, counts = counts)
            }
            if (s.endA + s.endB > 0) {
                Spacer(Modifier.height(4.dp))
                SplitBar("A end", s.endA, "B end", s.endB)
            }
        }

        StatSection("Days of the week") {
            val days = DayOfWeek.entries
            Bars(
                values = days.map { s.byWeekday[it] ?: 0 },
                labels = days.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                showValues = true,
            )
        }

        StatSection("Boarding times") {
            Bars(
                values = s.byHour.toList(),
                labels = listOf(0, 6, 12, 18).map(::hourLabel),
                showValues = false,
            )
        }

        if (s.byLine.isNotEmpty()) StatSection("Lines") {
            Ranked(s.byLine.mapNotNull { c -> Network.line(c.item)?.let { it to c.count } }) { line ->
                LineDots(listOf(line.color))
                Text(line.name, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (s.topCars.isNotEmpty()) StatSection("Most ridden cars") {
            Ranked(s.topCars.map { it.item to it.count }) { car ->
                Text("$car", style = MaterialTheme.typography.bodyLarge)
                CarSeries.of(car)?.let {
                    Text(it.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (s.topStations.isNotEmpty()) StatSection("Most used stations") {
            Ranked(s.topStations.map { it.item to it.count }) { id ->
                Text(Network.stationName(id) ?: id, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (s.topTrips.isNotEmpty()) StatSection("Most common trips") {
            Ranked(s.topTrips.map { it.item to it.count }) { (board, exit) ->
                TripLabel(board, exit, style = MaterialTheme.typography.bodyLarge, arrowSize = 16.dp)
            }
        }

        StatSection("Records") {
            s.earliest?.let { Fact("Earliest boarding", "${it.boardedAt.clock()} on ${it.date.format(shortDay)}") }
            s.latest?.let { Fact("Latest boarding", "${it.boardedAt.clock()} on ${it.date.format(shortDay)}") }
            s.longestRide?.let {
                Fact("Longest ride", listOfNotNull(minutesText(it.minutes!!), tripText(it.boardStation, it.exitStation)).joinToString(", "))
            }
            s.busiestDay?.let { Fact("Most rides in a day", "${it.count} on ${it.item.format(shortDay)}") }
        }

        if (s.brochures.isNotEmpty()) StatSection("Brochures") {
            Text(
                "${s.brochures.size} different, ${s.brochures.sumOf { it.count }} picked up in all",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (c in s.brochures) Fact(c.item, if (c.count == 1) "once" else "${c.count} times")
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun hours(minutes: Long): String =
    if (minutes < 600) minutesText(minutes) else "${(minutes / 60.0).roundToInt()} h"

private fun hourLabel(h: Int) = when (h) {
    0 -> "12 AM"
    12 -> "12 PM"
    else -> if (h < 12) "$h AM" else "${h - 12} PM"
}

/** Each block of statistics sits in its own panel under a section title. */
@Composable
private fun StatSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Section(title) { Panel(content = content) }
}

@Composable
private fun Tile(label: String, value: String, detail: String, modifier: Modifier, color: Color = MaterialTheme.colorScheme.surfaceBright) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = color) {
        Column(Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.displaySmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Column chart in one color. Values sit above the bars when there are few enough to read. */
@Composable
private fun Bars(values: List<Int>, labels: List<String>, showValues: Boolean) {
    val most = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.outlineVariant
    Column {
        if (showValues) Row(Modifier.fillMaxWidth()) {
            for (v in values) {
                Text(
                    if (v > 0) "$v" else "",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val slot = size.width / values.size
            val w = minOf(slot - 2.dp.toPx(), 28.dp.toPx())
            val r = minOf(4.dp.toPx(), w / 2)
            values.forEachIndexed { i, v ->
                if (v == 0) return@forEachIndexed
                val h = (size.height * v / most).coerceAtLeast(r * 2)
                val x = i * slot + (slot - w) / 2
                drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(r))
                // Square off the bottom so the bar sits flat on the baseline.
                drawRect(bar, Offset(x, size.height - r), Size(w, r))
            }
            drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }
        // With fewer labels than bars, each label marks the start of its stretch of bars.
        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                for (l in labels) {
                    Text(
                        l,
                        Modifier.weight(1f),
                        textAlign = if (showValues) TextAlign.Center else TextAlign.Start,
                        softWrap = false,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A short leaderboard: one row per item with its count at the end, separated by hairlines. */
@Composable
private fun <T> Ranked(items: List<Pair<T, Int>>, label: @Composable RowScope.(T) -> Unit) {
    Column {
        items.forEachIndexed { i, (item, count) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { label(item) }
                Text("$count", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SplitBar(leftLabel: String, left: Int, rightLabel: String, right: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$leftLabel  $left", style = MaterialTheme.typography.bodyMedium)
            Text("$right  $rightLabel", style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (left > 0) Box(Modifier.weight(left.toFloat()).height(8.dp).background(scheme.primary, RoundedCornerShape(3.dp)))
            if (right > 0) Box(Modifier.weight(right.toFloat()).height(8.dp).background(scheme.outline, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

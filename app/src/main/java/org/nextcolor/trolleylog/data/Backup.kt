package org.nextcolor.trolleylog.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Backup file format: a JSON object with a format marker and the rides. */
object Backup {
    private const val FORMAT = "trolleylog"
    /** Version 2 added the train part and place in car, and lets the train length be left out. */
    private const val VERSION = 2
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    class FormatException(message: String) : Exception(message)

    fun toJson(rides: List<Ride>): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("rides", JSONArray(rides.map(::rideToJson)))
        .toString(2)

    fun fromJson(text: String): List<Ride> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw FormatException("This file is not a Trolley Log backup.")
        }
        if (root.optString("format") != FORMAT) throw FormatException("This file is not a Trolley Log backup.")
        val version = root.optInt("version")
        if (version > VERSION) {
            throw FormatException("This backup was made with a newer version of Trolley Log.")
        }
        val array = root.optJSONArray("rides") ?: JSONArray()
        return try {
            (0 until array.length()).map { rideFromJson(array.getJSONObject(it), version) }
        } catch (e: Exception) {
            throw FormatException("This backup is damaged and could not be read.")
        }
    }

    private fun rideToJson(r: Ride): JSONObject = JSONObject()
        .put("id", r.id)
        .put("date", r.date.toString())
        .put("boardedAt", r.boardedAt.format(time))
        .putOpt("arrivedAt", r.arrivedAt?.format(time))
        .put("car", r.car)
        .putOpt("position", r.position)
        .putOpt("trainLength", r.trainLength)
        .putOpt("trainPart", r.trainPart?.name?.lowercase())
        .putOpt("placeInCar", r.placeInCar?.name?.lowercase())
        .putOpt("boardStation", r.boardStation)
        .putOpt("exitStation", r.exitStation)
        .putOpt("line", r.line)
        .put("brochures", JSONArray(r.brochures))
        .put("note", r.note)

    private fun rideFromJson(o: JSONObject, version: Int): Ride {
        val brochures = o.optJSONArray("brochures") ?: JSONArray()
        return Ride(
            id = o.getString("id"),
            date = LocalDate.parse(o.getString("date")),
            boardedAt = LocalTime.parse(o.getString("boardedAt")),
            arrivedAt = o.optStringOrNull("arrivedAt")?.let(LocalTime::parse),
            car = o.optString("car"),
            position = if (o.has("position")) o.getInt("position") else null,
            // Version 1 always had a train length and fell back to the default without one.
            trainLength = when {
                o.has("trainLength") -> o.getInt("trainLength")
                version < 2 -> Ride.DEFAULT_TRAIN_LENGTH
                else -> null
            },
            trainPart = o.optStringOrNull("trainPart")?.let { TrainPart.valueOf(it.uppercase()) },
            placeInCar = o.optStringOrNull("placeInCar")?.let { PlaceInCar.valueOf(it.uppercase()) },
            boardStation = o.optStringOrNull("boardStation"),
            exitStation = o.optStringOrNull("exitStation"),
            line = o.optStringOrNull("line"),
            brochures = (0 until brochures.length()).map(brochures::getString),
            note = o.optString("note"),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private const val BROCHURE_SEPARATOR = " | "

    /**
     * A spreadsheet-friendly copy. Station and line names are written out in
     * full. Car in train is a number from the front when the train length is
     * known, and front, middle, or rear when it is not.
     */
    fun toCsv(rides: List<Ride>): String = buildString {
        appendLine(
            "Date,Car,Car series,Car in train,Train length,Place in car,Boarded,Arrived,Minutes," +
                "Boarded at,Disembarked at,Line,Brochures,Note"
        )
        for (r in rides.sortedWith(compareBy({ it.date }, { it.boardedAt }))) {
            val cells = listOf(
                r.date.toString(),
                r.car,
                r.carId?.series?.label.orEmpty(),
                r.position?.toString() ?: r.trainPart?.name?.lowercase().orEmpty(),
                r.trainLength?.toString().orEmpty(),
                r.placeInCar?.name?.lowercase().orEmpty(),
                r.boardedAt.format(time),
                r.arrivedAt?.format(time).orEmpty(),
                r.minutes?.toString().orEmpty(),
                Network.stationName(r.boardStation).orEmpty(),
                Network.stationName(r.exitStation).orEmpty(),
                Network.line(r.line)?.name.orEmpty(),
                r.brochures.joinToString(BROCHURE_SEPARATOR),
                r.note,
            )
            appendLine(cells.joinToString(",", transform = ::csvCell))
        }
    }

    private fun csvCell(s: String): String =
        if (s.any { it in ",\"\n\r" }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** Rides read from a spreadsheet, and how many rows had no date or boarding time to make a ride from. */
    data class Sheet(val rides: List<Ride>, val skipped: Int)

    private val clock = DateTimeFormatter.ofPattern("H:mm")
    private val ordinal = Regex("""(\d+)(st|nd|rd|th)""")

    /**
     * Reads a spreadsheet with the columns [toCsv] writes, in any order and
     * matched by name. Missing columns are left empty and unknown ones are
     * ignored. Each ride's id comes from its date, boarding time, and car, so
     * the same row always makes the same ride.
     */
    fun fromCsv(text: String): Sheet {
        val rows = csvRows(text.removePrefix("﻿"))
        val header = rows.firstOrNull().orEmpty().map { it.trim().lowercase() }
        if ("date" !in header || "boarded" !in header) {
            throw FormatException("This file has no Date and Boarded columns.")
        }
        val rides = ArrayList<Ride>()
        var skipped = 0
        for (cells in rows.drop(1)) {
            if (cells.all { it.isBlank() }) continue
            fun cell(name: String): String = cells.getOrNull(header.indexOf(name))?.trim().orEmpty()
            val ride = rideFromRow(::cell)
            if (ride == null) skipped++ else rides += ride
        }
        return Sheet(rides, skipped)
    }

    private fun rideFromRow(cell: (String) -> String): Ride? {
        val date = runCatching { LocalDate.parse(cell("date")) }.getOrNull() ?: return null
        val boarded = runCatching { LocalTime.parse(cell("boarded"), clock) }.getOrNull() ?: return null
        val arrived = runCatching { LocalTime.parse(cell("arrived"), clock) }.getOrNull()
        val car = cell("car").uppercase()
        val length = cell("train length").toIntOrNull()?.takeIf { it in 1..Ride.MAX_TRAIN_LENGTH }
        val board = station(cell("boarded at"))
        val exit = station(cell("disembarked at"))
        val named = cell("line").lowercase().let { n -> Network.lines.firstOrNull { it.name.lowercase() == n || it.id == n } }
        val (position, part) = carInTrain(cell("car in train").lowercase(), length)
        return Ride(
            id = UUID.nameUUIDFromBytes("$date $boarded $car".toByteArray()).toString(),
            date = date,
            boardedAt = boarded,
            arrivedAt = arrived,
            car = car,
            position = position,
            trainLength = length,
            trainPart = part,
            placeInCar = PlaceInCar.entries.firstOrNull { it.label.equals(cell("place in car"), ignoreCase = true) },
            boardStation = board,
            exitStation = exit,
            line = named?.id ?: Network.linesBetween(board, exit).singleOrNull()?.id,
            brochures = cell("brochures").split("|").map { it.trim() }.filter { it.isNotEmpty() },
            note = cell("note"),
        )
    }

    private fun station(name: String): String? =
        Network.stations.firstOrNull { it.name.equals(name, ignoreCase = true) || it.id == name }?.id

    /**
     * Car in train as a position when the train length is known, otherwise
     * as front, middle, or rear. Takes a number, an ordinal such as 2nd, or
     * the words front, middle, rear, and only.
     */
    private fun carInTrain(text: String, length: Int?): Pair<Int?, TrainPart?> {
        val number = text.toIntOrNull() ?: ordinal.matchEntire(text)?.groupValues?.get(1)?.toInt()
        val part = TrainPart.entries.firstOrNull { it.label.lowercase() == text }
            ?: TrainPart.FRONT.takeIf { text == "only" || number == 1 }
        return when {
            length != null && number != null -> number.takeIf { it in 1..length } to null
            length != null && part != null -> Ride.positionOf(part, length) to null
            length == null -> null to part
            else -> null to null
        }
    }

    /** Splits CSV text into rows of cells. Quoted cells can hold commas, doubled quotes, and line breaks. */
    private fun csvRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                quoted -> cell.append(c)
                c == ',' -> { row += cell.toString(); cell.clear() }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString()
                    cell.clear()
                    rows += row
                    row = ArrayList()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }
}

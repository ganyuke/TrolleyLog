package org.nextcolor.trolleylog.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Backup file format: a JSON object with a format marker and the rides. */
object Backup {
    private const val FORMAT = "trolleylog"
    private const val VERSION = 1
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
        if (root.optInt("version") > VERSION) {
            throw FormatException("This backup was made with a newer version of Trolley Log.")
        }
        val array = root.optJSONArray("rides") ?: JSONArray()
        return try {
            (0 until array.length()).map { rideFromJson(array.getJSONObject(it)) }
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
        .put("trainLength", r.trainLength)
        .putOpt("boardStation", r.boardStation)
        .putOpt("exitStation", r.exitStation)
        .putOpt("line", r.line)
        .put("brochures", JSONArray(r.brochures))
        .put("note", r.note)

    private fun rideFromJson(o: JSONObject): Ride {
        val brochures = o.optJSONArray("brochures") ?: JSONArray()
        return Ride(
            id = o.getString("id"),
            date = LocalDate.parse(o.getString("date")),
            boardedAt = LocalTime.parse(o.getString("boardedAt")),
            arrivedAt = o.optStringOrNull("arrivedAt")?.let(LocalTime::parse),
            car = o.optString("car"),
            position = if (o.has("position")) o.getInt("position") else null,
            trainLength = o.optInt("trainLength", Ride.DEFAULT_TRAIN_LENGTH),
            boardStation = o.optStringOrNull("boardStation"),
            exitStation = o.optStringOrNull("exitStation"),
            line = o.optStringOrNull("line"),
            brochures = (0 until brochures.length()).map(brochures::getString),
            note = o.optString("note"),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    /** A spreadsheet-friendly copy. Station and line names are written out in full. */
    fun toCsv(rides: List<Ride>): String = buildString {
        appendLine(
            "Date,Car,Car series,Car in train,Train length,Boarded,Arrived,Minutes," +
                "Boarded at,Disembarked at,Line,Brochures,Note"
        )
        for (r in rides.sortedWith(compareBy({ it.date }, { it.boardedAt }))) {
            val cells = listOf(
                r.date.toString(),
                r.car,
                r.carId?.series?.label.orEmpty(),
                r.position?.toString().orEmpty(),
                r.trainLength.toString(),
                r.boardedAt.format(time),
                r.arrivedAt?.format(time).orEmpty(),
                r.minutes?.toString().orEmpty(),
                Network.stationName(r.boardStation).orEmpty(),
                Network.stationName(r.exitStation).orEmpty(),
                Network.line(r.line)?.name.orEmpty(),
                r.brochures.joinToString(" | "),
                r.note,
            )
            appendLine(cells.joinToString(",", transform = ::csvCell))
        }
    }

    private fun csvCell(s: String): String =
        if (s.any { it in ",\"\n\r" }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

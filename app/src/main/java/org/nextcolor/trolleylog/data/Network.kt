package org.nextcolor.trolleylog.data

import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.hypot

data class Station(val id: String, val name: String, val lat: Double, val lon: Double)

/** [path] is the track as flattened latitude, longitude pairs. */
class Line(
    val id: String,
    val name: String,
    val color: Long,
    val stationIds: List<String>,
    val path: FloatArray,
)

object Network {
    val stations: List<Station> = NETWORK_STATIONS
    val lines: List<Line> = NETWORK_LINES

    private val byId = stations.associateBy { it.id }
    private val linesById = lines.associateBy { it.id }

    fun station(id: String?): Station? = id?.let { byId[it] }
    fun line(id: String?): Line? = id?.let { linesById[it] }
    fun stationName(id: String?): String? = station(id)?.name

    fun linesAt(stationId: String): List<Line> = lines.filter { stationId in it.stationIds }

    /** Lines that run between the two stations without a transfer. */
    fun linesBetween(a: String?, b: String?): List<Line> =
        if (a == null || b == null) emptyList()
        else lines.filter { a in it.stationIds && b in it.stationIds }

    private fun km(a: Station, b: Station): Double {
        val k = cos(Math.toRadians((a.lat + b.lat) / 2))
        return hypot((a.lat - b.lat) * 111.32, (a.lon - b.lon) * 111.32 * k)
    }

    private val neighbours: Map<String, List<Pair<String, Double>>> by lazy {
        val out = HashMap<String, MutableList<Pair<String, Double>>>()
        for (line in lines) for ((a, b) in line.stationIds.zipWithNext()) {
            val d = km(byId.getValue(a), byId.getValue(b))
            out.getOrPut(a) { mutableListOf() } += b to d
            out.getOrPut(b) { mutableListOf() } += a to d
        }
        out
    }

    /** Shortest distance along the tracks, measured station to station. */
    fun trackKm(from: String?, to: String?): Double? {
        if (from == null || to == null || from !in byId || to !in byId) return null
        val best = HashMap<String, Double>().apply { put(from, 0.0) }
        val queue = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        queue += from to 0.0
        while (queue.isNotEmpty()) {
            val (at, d) = queue.poll()!!
            if (at == to) return d
            if (d > best.getValue(at)) continue
            for ((next, step) in neighbours[at].orEmpty()) {
                val nd = d + step
                if (nd < (best[next] ?: Double.MAX_VALUE)) {
                    best[next] = nd
                    queue += next to nd
                }
            }
        }
        return null
    }
}

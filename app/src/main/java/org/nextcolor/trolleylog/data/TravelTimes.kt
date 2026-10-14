package org.nextcolor.trolleylog.data

import java.util.PriorityQueue

/**
 * Scheduled running times of one line. [outbound] is the seconds from each
 * station to the next in the line's station order, [inbound] the same
 * stretches ridden the other way. [outboundEvery] and [inboundEvery] are the
 * seconds between trains on each stretch, which differ where some trains
 * turn back partway along the line.
 */
class LineTimes(
    val lineId: String,
    val outbound: IntArray,
    val inbound: IntArray,
    val outboundEvery: IntArray,
    val inboundEvery: IntArray,
)

/** How long a trip takes by the timetable, for rides logged without an arrival time. */
object TravelTimes {
    private val byLine = TRAVEL_TIMES.associateBy { it.lineId }

    /**
     * Scheduled minutes from [board] to [exit]. A ride is one train, so a line
     * serving both stations is always taken over a quicker way round with a
     * change, and [line] picks between lines that both do. Only stations with
     * no line in common are routed with transfers.
     */
    fun minutes(board: String?, exit: String?, line: String? = null): Int? {
        if (board == null || exit == null || board == exit) return null
        val direct = Network.linesBetween(board, exit)
        val seconds = direct.firstOrNull { it.id == line }?.let { along(it, board, exit) }
            ?: direct.mapNotNull { along(it, board, exit) }.minOrNull()
            ?: withTransfers(board, exit)
            ?: return null
        return (seconds + 30) / 60
    }

    private fun along(line: Line, from: String, to: String): Int? {
        val times = byLine[line.id] ?: return null
        val a = line.stationIds.indexOf(from)
        val b = line.stationIds.indexOf(to)
        return if (a < b) (a until b).sumOf { times.outbound[it] } else (b until a).sumOf { times.inbound[it] }
    }

    /**
     * Waiting for a train that goes all the way from [from] to [to]: half the
     * time between trains on the least served stretch of the way.
     */
    private fun waitFor(line: Line, from: String, to: String): Int {
        val times = byLine[line.id] ?: return 0
        val a = line.stationIds.indexOf(from)
        val b = line.stationIds.indexOf(to)
        val every = if (a < b) (a until b).maxOf { times.outboundEvery[it] } else (b until a).maxOf { times.inboundEvery[it] }
        return every / 2
    }

    /**
     * Quickest way over several lines. Each leg is one train from a station
     * to any other on the same line, and every leg after the first starts
     * with a wait.
     */
    private fun withTransfers(from: String, to: String): Int? {
        val best = HashMap<String, Int>().apply { put(from, 0) }
        val queue = PriorityQueue<Pair<String, Int>>(compareBy { it.second })
        queue += from to 0
        while (queue.isNotEmpty()) {
            val (at, seconds) = queue.poll()!!
            if (at == to) return seconds
            if (seconds > best.getValue(at)) continue
            for (line in Network.linesAt(at)) for (next in line.stationIds) {
                if (next == at) continue
                val ride = along(line, at, next) ?: continue
                val total = seconds + ride + if (at == from) 0 else waitFor(line, at, next)
                if (total < (best[next] ?: Int.MAX_VALUE)) {
                    best[next] = total
                    queue += next to total
                }
            }
        }
        return null
    }
}

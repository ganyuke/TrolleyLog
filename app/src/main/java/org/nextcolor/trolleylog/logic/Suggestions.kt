package org.nextcolor.trolleylog.logic

import org.nextcolor.trolleylog.data.Network
import org.nextcolor.trolleylog.data.Ride
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

/** Why the stations of a new ride were filled in. */
enum class Guess { NONE, HABIT, CONTINUING, GOING_BACK }

data class Suggestion(
    val guess: Guess = Guess.NONE,
    val boardStation: String? = null,
    val exitStation: String? = null,
    val line: String? = null,
    val position: Int? = null,
    val trainLength: Int = Ride.DEFAULT_TRAIN_LENGTH,
    /** Likely trips as board and exit station, best first. The first is the one filled in. */
    val trips: List<Pair<String, String>> = emptyList(),
)

object Suggestions {
    /** After disembarking, a new boarding this soon is treated as a transfer, not the trip back. */
    private val TRANSFER_WINDOW = Duration.ofMinutes(20)
    /** Used instead when the last ride has no arrival time and only the boarding time is known. */
    private val TRANSFER_WINDOW_FROM_BOARDING = Duration.ofMinutes(60)
    private const val NEARBY_MINUTES = 90
    private const val MAX_TRIPS = 3

    fun forNewRide(rides: List<Ride>, now: LocalDateTime): Suggestion {
        val today = rides.filter { it.date == now.toLocalDate() }.sortedBy { it.boardedAt }
        val earlier = rides.filter { it.date < now.toLocalDate() || it in today }
        val trainLength = rides.sortedWith(compareBy({ it.date }, { it.boardedAt }))
            .takeLast(10).mostCommon { it.trainLength } ?: Ride.DEFAULT_TRAIN_LENGTH

        var guess = Guess.NONE
        var board: String? = null
        var exit: String? = null

        val last = today.lastOrNull()
        if (last?.exitStation != null) {
            board = last.exitStation
            val since = Duration.between(last.arrivedAt ?: last.boardedAt, now.toLocalTime())
            val window = if (last.arrivedAt != null) TRANSFER_WINDOW else TRANSFER_WINDOW_FROM_BOARDING
            val legBack = openLegs(today).lastOrNull()
            if (since > window && legBack != null && legBack.second == board) {
                guess = Guess.GOING_BACK
                exit = legBack.first
            } else {
                guess = Guess.CONTINUING
                exit = usualExit(earlier, board, now, avoid = last.boardStation)
            }
        } else {
            board = usualBoard(earlier, now)
            if (board != null) {
                guess = Guess.HABIT
                exit = usualExit(earlier, board, now, avoid = null)
            }
        }

        // Other likely trips to offer next to the one filled in: first from
        // the station the rider is probably at, then the usual ones for this time.
        val trips = buildList {
            if (board != null && exit != null) add(board to exit)
            if (board != null) addAll(usualTrips(earlier.filter { it.boardStation == board }, now))
            addAll(usualTrips(earlier, now))
        }.distinct().take(MAX_TRIPS)

        return Suggestion(
            trips = trips,
            guess = guess,
            boardStation = board,
            exitStation = exit,
            line = usualLine(rides, board, exit),
            position = usualPosition(rides, board, exit),
            trainLength = trainLength,
        )
    }

    /**
     * Today's legs that have not been ridden back yet, oldest first. Riding
     * A to B and later B to A cancels out, so what is left is the way home.
     */
    private fun openLegs(today: List<Ride>): List<Pair<String, String>> {
        val legs = ArrayList<Pair<String, String>>()
        for (r in today) {
            val a = r.boardStation ?: continue
            val b = r.exitStation ?: continue
            if (legs.lastOrNull() == (b to a)) legs.removeAt(legs.lastIndex) else legs += a to b
        }
        return legs
    }

    /** Rides at a similar time of day count more, and more again on the same kind of day. */
    private fun weight(ride: Ride, now: LocalDateTime): Int {
        val near = minutesApart(ride.boardedAt, now.toLocalTime()) <= NEARBY_MINUTES
        val sameKind = ride.date.dayOfWeek.isWeekend() == now.dayOfWeek.isWeekend()
        return when {
            near && sameKind -> 6
            near -> 3
            else -> 1
        }
    }

    private fun usualTrips(rides: List<Ride>, now: LocalDateTime): List<Pair<String, String>> =
        rides.filter { it.boardStation != null && it.exitStation != null }
            .groupBy { it.boardStation!! to it.exitStation!! }
            .entries.sortedByDescending { (_, group) -> group.sumOf { weight(it, now) } }
            .map { it.key }

    private fun usualBoard(rides: List<Ride>, now: LocalDateTime): String? =
        rides.filter { it.boardStation != null }
            .groupBy { it.boardStation }
            .maxByOrNull { (_, group) -> group.sumOf { weight(it, now) } }?.key

    private fun usualExit(rides: List<Ride>, board: String, now: LocalDateTime, avoid: String?): String? =
        rides.filter { it.boardStation == board && it.exitStation != null && it.exitStation != avoid }
            .groupBy { it.exitStation }
            .maxByOrNull { (_, group) -> group.sumOf { weight(it, now) } }?.key

    /** Only matters where two lines share track, e.g. Santa Fe Depot to Old Town. */
    fun usualLine(rides: List<Ride>, board: String?, exit: String?): String? {
        val candidates = Network.linesBetween(board, exit).map { it.id }
        if (candidates.size <= 1) return candidates.firstOrNull()
        return rides.filter { it.boardStation == board && it.exitStation == exit && it.line in candidates }
            .mostCommon { it.line }
    }

    /**
     * People wait at the same spot on a platform. A position is only offered
     * when the same trip was logged at least three times and mostly in one car.
     */
    private fun usualPosition(rides: List<Ride>, board: String?, exit: String?): Int? {
        if (board == null) return null
        val same = rides.filter { it.boardStation == board && it.exitStation == exit && it.position != null }
        if (same.size < 3) return null
        val (position, count) = same.groupingBy { it.position }.eachCount().maxByOrNull { it.value } ?: return null
        return position.takeIf { count * 10 >= same.size * 6 }
    }

    private fun minutesApart(a: LocalTime, b: LocalTime): Int {
        val d = abs(a.toSecondOfDay() - b.toSecondOfDay()) / 60
        return minOf(d, 24 * 60 - d)
    }

    private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

    private fun <T, K> List<T>.mostCommon(key: (T) -> K?): K? =
        mapNotNull(key).groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
}

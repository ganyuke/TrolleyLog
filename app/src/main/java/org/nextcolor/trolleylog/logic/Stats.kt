package org.nextcolor.trolleylog.logic

import org.nextcolor.trolleylog.data.CarSeries
import org.nextcolor.trolleylog.data.Network
import org.nextcolor.trolleylog.data.PlaceInCar
import org.nextcolor.trolleylog.data.Ride
import org.nextcolor.trolleylog.data.TrainPart
import org.nextcolor.trolleylog.data.TravelTimes
import java.time.DayOfWeek
import java.time.LocalDate

data class Count<T>(val item: T, val count: Int)

data class Stats(
    val rides: Int,
    val days: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val busiestDay: Count<LocalDate>?,
    /** Logged minutes, plus the scheduled time of rides that have no arrival time. */
    val minutesOnBoard: Long,
    val timedRides: Int,
    /** How many of the [timedRides] went in at their scheduled time. */
    val estimatedRides: Int,
    val longestRide: Ride?,
    val km: Double,
    val carsRidden: Map<CarSeries, Set<Int>>,
    val topCars: List<Count<Int>>,
    val endA: Int,
    val endB: Int,
    /** Rides per car position, keyed by train length, then position from the front. */
    val positions: Map<Int, Map<Int, Int>>,
    /** Rides per front, middle, or rear car, for rides with no train length. */
    val parts: Map<TrainPart, Int>,
    val places: Map<PlaceInCar, Int>,
    val byWeekday: Map<DayOfWeek, Int>,
    val byHour: IntArray,
    val byLine: List<Count<String>>,
    val topStations: List<Count<String>>,
    val stationsVisited: Int,
    val topTrips: List<Count<Pair<String, String>>>,
    val brochures: List<Count<String>>,
    val earliest: Ride?,
    val latest: Ride?,
) {
    companion object {
        /** Late-night rides before this hour count as the end of the day, not an early start. */
        private const val DAY_STARTS_AT_HOUR = 3

        fun of(rides: List<Ride>, today: LocalDate): Stats {
            val dates = rides.map { it.date }.toSortedSet()
            var longest = 0
            var run = 0
            var previous: LocalDate? = null
            for (d in dates) {
                run = if (previous?.plusDays(1) == d) run + 1 else 1
                longest = maxOf(longest, run)
                previous = d
            }
            // A streak is still alive if today has no ride yet but yesterday had one.
            var current = 0
            var day = if (today in dates) today else today.minusDays(1)
            while (day in dates) {
                current++
                day = day.minusDays(1)
            }

            val cars = rides.mapNotNull { it.carId }
            val ridden = cars.filter { it.series != null }
                .groupBy({ it.series!! }, { it.number })
                .mapValues { it.value.toSet() }
            val stationCounts = rides.flatMap { listOfNotNull(it.boardStation, it.exitStation) }
                .groupingBy { it }.eachCount()
            val timed = rides.filter { it.minutes != null }
            val estimates = rides.filter { it.minutes == null }
                .mapNotNull { TravelTimes.minutes(it.boardStation, it.exitStation, it.line) }
            val dayOrder = compareBy<Ride> { (it.boardedAt.hour - DAY_STARTS_AT_HOUR + 24) % 24 }
                .thenBy { it.boardedAt.minute }

            return Stats(
                rides = rides.size,
                days = dates.size,
                currentStreak = current,
                longestStreak = longest,
                busiestDay = rides.groupingBy { it.date }.eachCount().top(1).firstOrNull(),
                minutesOnBoard = timed.sumOf { it.minutes!! } + estimates.sum(),
                timedRides = timed.size + estimates.size,
                estimatedRides = estimates.size,
                longestRide = timed.maxByOrNull { it.minutes!! },
                km = rides.sumOf { Network.trackKm(it.boardStation, it.exitStation) ?: 0.0 },
                carsRidden = ridden,
                topCars = cars.groupingBy { it.number }.eachCount().top(5),
                endA = cars.count { it.end == 'A' },
                endB = cars.count { it.end == 'B' },
                positions = rides.filter { it.position != null && it.trainLength != null }
                    .groupBy { it.trainLength!! }
                    .mapValues { (_, group) -> group.groupingBy { it.position!! }.eachCount() },
                parts = rides.filter { it.trainLength == null }.mapNotNull { it.trainPart }.groupingBy { it }.eachCount(),
                places = rides.mapNotNull { it.placeInCar }.groupingBy { it }.eachCount(),
                byWeekday = rides.groupingBy { it.date.dayOfWeek }.eachCount(),
                byHour = IntArray(24).also { hours -> rides.forEach { hours[it.boardedAt.hour]++ } },
                byLine = rides.mapNotNull { it.line }.groupingBy { it }.eachCount().top(10),
                topStations = stationCounts.top(5),
                stationsVisited = stationCounts.size,
                topTrips = rides.filter { it.boardStation != null && it.exitStation != null }
                    .groupingBy { it.boardStation!! to it.exitStation!! }.eachCount().top(3),
                brochures = rides.flatMap { it.brochures }.groupingBy { it }.eachCount().top(100),
                earliest = rides.minWithOrNull(dayOrder),
                latest = rides.maxWithOrNull(dayOrder),
            )
        }

        private fun <T> Map<T, Int>.top(n: Int): List<Count<T>> =
            entries.sortedByDescending { it.value }.take(n).map { Count(it.key, it.value) }
    }
}

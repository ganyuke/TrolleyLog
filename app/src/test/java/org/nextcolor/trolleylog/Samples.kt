package org.nextcolor.trolleylog

import org.nextcolor.trolleylog.data.Ride
import java.time.LocalDate
import java.time.LocalTime

/** A few weeks of a UTC to Old Town commute, with some weekend trips, for tests and renders. */
object Samples {
    val today: LocalDate = LocalDate.of(2026, 10, 13)

    fun ride(
        id: String, date: LocalDate, boarded: String, arrived: String? = null, car: String = "",
        position: Int? = null, length: Int = 3, from: String? = null, to: String? = null,
        line: String? = null, brochures: List<String> = emptyList(),
    ) = Ride(
        id, date, LocalTime.parse(boarded), arrived?.let(LocalTime::parse), car, position, length,
        from, to, line, brochures,
    )

    val history: List<Ride> = buildList {
        val cars = listOf("4029B", "5009A", "4051A", "5062B", "3004A", "4029A", "5077B", "4012B", "5009B", "5033A")
        var n = 0
        for (back in 1..21) {
            val day = today.minusDays(back.toLong())
            if (day.dayOfWeek.value >= 6) {
                if (back % 2 == 0) {
                    add(ride("w$back", day, "11:20", "11:52", cars[n++ % cars.size], 1, 2, "oldS", "sdsS", "green", listOf("Green Line timetable")))
                }
                continue
            }
            add(ride("a$back", day, "07:4${back % 6}", "08:1${back % 5}", cars[n++ % cars.size], 2, 3, "utcS", "oldS", "blue",
                if (back == 3) listOf("Rider Alert", "PRONTO guide") else emptyList()))
            add(ride("b$back", day, "17:2${back % 4}", "17:5${back % 7}", cars[n++ % cars.size], if (back % 4 == 0) 3 else 1, 3, "oldS", "utcS", "blue"))
        }
    }

    val morning = ride("t1", today, "07:44", "08:12", "4029B", 2, 3, "utcS", "oldS", "blue", listOf("Rider Alert"))
}

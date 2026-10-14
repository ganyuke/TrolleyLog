package org.nextcolor.trolleylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nextcolor.trolleylog.data.Backup
import org.nextcolor.trolleylog.data.CarId
import org.nextcolor.trolleylog.data.CarSeries
import org.nextcolor.trolleylog.data.Network
import org.nextcolor.trolleylog.data.RideStore
import org.nextcolor.trolleylog.logic.Guess
import org.nextcolor.trolleylog.logic.Stats
import org.nextcolor.trolleylog.logic.Suggestions
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class LogicTest {
    private val today = Samples.today
    private fun at(time: String) = today.atTime(LocalTime.parse(time))

    @Test fun carNumbers() {
        assertEquals(CarId(4029, 'B'), CarId.parse("4029b"))
        assertEquals(CarSeries.S70US, CarId.parse("4029B")!!.series)
        assertEquals(CarSeries.S700, CarId.parse("5009A")!!.series)
        assertEquals(CarSeries.S70, CarId.parse("3004")!!.series)
        assertNull(CarId.parse("4029")!!.end)
        assertNull(CarId.parse("9999A")!!.series)
        assertNull(CarId.parse("40B9"))
    }

    @Test fun networkIsWhole() {
        assertEquals(4, Network.lines.size)
        for (line in Network.lines) for (id in line.stationIds) assertTrue(id, Network.station(id) != null)
        assertEquals(listOf("blue", "green"), Network.linesBetween("sfdS", "oldS").map { it.id })
        // UTC to Old Town is about 11 miles by rail.
        assertEquals(17.5, Network.trackKm("utcS", "oldS")!!, 2.0)
        // Santee to San Ysidro needs transfers and still has a distance.
        assertTrue(Network.trackKm("sanS", "sayS")!! > 40)
    }

    @Test fun usualTripOnAFreshMorning() {
        val s = Suggestions.forNewRide(Samples.history, at("07:40"))
        assertEquals(Guess.HABIT, s.guess)
        assertEquals("utcS", s.boardStation)
        assertEquals("oldS", s.exitStation)
        assertEquals("blue", s.line)
        assertEquals(2, s.position)
        assertEquals(3, s.trainLength)
    }

    @Test fun usualTripWhenTheMorningWasNotLogged() {
        val s = Suggestions.forNewRide(Samples.history, at("17:25"))
        assertEquals("oldS", s.boardStation)
        assertEquals("utcS", s.exitStation)
    }

    @Test fun goingBackInTheEvening() {
        val s = Suggestions.forNewRide(Samples.history + Samples.morning, at("17:20"))
        assertEquals(Guess.GOING_BACK, s.guess)
        assertEquals("oldS", s.boardStation)
        assertEquals("utcS", s.exitStation)
        // The filled-in trip leads the choices, followed by other trips made from there.
        assertEquals(listOf("oldS" to "utcS", "oldS" to "sdsS", "utcS" to "oldS"), s.trips)
    }

    @Test fun transferRightAfterArriving() {
        val s = Suggestions.forNewRide(Samples.history + Samples.morning, at("08:18"))
        assertEquals(Guess.CONTINUING, s.guess)
        assertEquals("oldS", s.boardStation)
        // Not straight back to where the last ride started.
        assertTrue(s.exitStation != "utcS")
    }

    @Test fun goingBackRetracesATransfer() {
        val legs = listOf(
            Samples.ride("x1", today, "07:00", "07:30", from = "utcS", to = "oldS"),
            Samples.ride("x2", today, "07:35", "07:50", from = "oldS", to = "favS"),
        )
        val first = Suggestions.forNewRide(legs, at("16:00"))
        assertEquals("favS" to "oldS", first.boardStation to first.exitStation)
        val second = Suggestions.forNewRide(
            legs + Samples.ride("x3", today, "16:00", "16:15", from = "favS", to = "oldS"), at("16:40"),
        )
        assertEquals(Guess.GOING_BACK, second.guess)
        assertEquals("oldS" to "utcS", second.boardStation to second.exitStation)
    }

    @Test fun nothingToGoOn() {
        val s = Suggestions.forNewRide(emptyList(), at("09:00"))
        assertEquals(Guess.NONE, s.guess)
        assertNull(s.boardStation)
        assertTrue(s.trips.isEmpty())
        assertEquals(3, s.trainLength)
    }

    @Test fun statistics() {
        val rides = Samples.history + Samples.morning
        val s = Stats.of(rides, today)
        assertEquals(rides.size, s.rides)
        assertEquals(rides.map { it.date }.distinct().size, s.days)
        assertTrue(s.currentStreak >= 1)
        assertTrue(s.longestStreak >= s.currentStreak)
        assertEquals(4029, s.topCars.first().item)
        assertEquals(3, s.stationsVisited)
        assertTrue(s.km > 300)
        assertEquals("07:40", s.earliest!!.boardedAt.toString())
        assertEquals(rides.count { it.car.endsWith("A") }, s.endA)
    }

    @Test fun rideOverMidnight() {
        assertEquals(25L, Samples.ride("m", today, "23:50", "00:15").minutes)
    }

    @Test fun backupRoundTrip() {
        val rides = Samples.history + Samples.ride("bare", today, "09:00")
        assertEquals(rides, Backup.fromJson(Backup.toJson(rides)))
        assertThrows(Backup.FormatException::class.java) { Backup.fromJson("{\"rides\": []}") }
        assertThrows(Backup.FormatException::class.java) { Backup.fromJson("Date,Car") }
    }

    @Test fun spreadsheet() {
        val csv = Backup.toCsv(listOf(Samples.morning.copy(note = "Loud, \"fun\" car")))
        val row = csv.lines()[1]
        assertTrue(row, row.startsWith("2026-10-13,4029B,S70 US,2,3,07:44,08:12,28,UTC,Old Town,Blue Line,Rider Alert,"))
        assertTrue(row, row.endsWith("\"Loud, \"\"fun\"\" car\""))
    }

    @Test fun storeKeepsRidesAndImportsOnlyNewOnes() {
        val file = File.createTempFile("rides", ".json").apply { delete() }
        val store = RideStore(file)
        store.save(Samples.morning)
        store.save(Samples.morning.copy(car = "5009A"))
        assertEquals(listOf("5009A"), RideStore(file).rides.value.map { it.car })

        val result = store.import(Samples.history.take(3) + Samples.morning)
        assertEquals(RideStore.ImportResult(added = 3, alreadyHere = 1), result)
        assertEquals("5009A", store.rides.value.single { it.id == "t1" }.car)
        store.delete("t1")
        assertEquals(3, RideStore(file).rides.value.size)
        file.delete()
    }
}

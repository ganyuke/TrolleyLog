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
import org.nextcolor.trolleylog.data.PlaceInCar
import org.nextcolor.trolleylog.data.Ride
import org.nextcolor.trolleylog.data.RideStore
import org.nextcolor.trolleylog.data.TrainPart
import org.nextcolor.trolleylog.data.TravelTimes
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

    @Test fun scheduledRideTimes() {
        assertEquals(6, TravelTimes.minutes("msdS", "sdsS"))
        assertEquals(17, TravelTimes.minutes("oldS", "msdS"))
        assertNull(TravelTimes.minutes("msdS", null))
        assertNull(TravelTimes.minutes("msdS", "msdS"))
        for (a in Network.stations) for (b in Network.stations) {
            if (a != b) assertTrue("${a.name} to ${b.name}", TravelTimes.minutes(a.id, b.id)!! > 0)
        }
        // El Cajon to 12th & Imperial: the Orange Line is the short way, the Green Line goes round by Old Town.
        val orange = TravelTimes.minutes("elcS", "12tS", "orange")!!
        assertEquals(orange, TravelTimes.minutes("elcS", "12tS"))
        assertTrue(TravelTimes.minutes("elcS", "12tS", "green")!! > orange)
        // Grossmont to Santa Fe Depot stays on the Green Line all the way round, with no change downtown.
        assertEquals(41, TravelTimes.minutes("groS", "sfdS"))
        // UTC to SDSU has no line in common, so the wait at Old Town is added to the two legs.
        val legs = TravelTimes.minutes("utcS", "oldS")!! + TravelTimes.minutes("oldS", "sdsS")!!
        assertEquals(legs + 8, TravelTimes.minutes("utcS", "sdsS"))
        // The Blue Line runs twice as often south of America Plaza, so the wait for it there is half as long.
        val south = TravelTimes.minutes("eucS", "12tS")!! + TravelTimes.minutes("12tS", "iriS")!!
        assertEquals(south + 4, TravelTimes.minutes("eucS", "iriS"))
    }

    @Test fun rideTimeIsEstimatedWithoutAnArrival() {
        val logged = Samples.ride("a", today, "07:00", "07:10", from = "msdS", to = "sdsS")
        val open = Samples.ride("b", today, "17:00", from = "sdsS", to = "msdS")
        val bare = Samples.ride("c", today, "18:00")
        assertEquals(0, Stats.of(listOf(logged), today).estimatedRides)
        val s = Stats.of(listOf(logged, open, bare), today)
        assertEquals(16L, s.minutesOnBoard)
        assertEquals(2, s.timedRides)
        assertEquals(1, s.estimatedRides)
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
        assertTrue(row, row.startsWith("2026-10-13,4029B,S70 US,2,3,,07:44,08:12,28,UTC,Old Town,Blue Line,Rider Alert,"))
        assertTrue(row, row.endsWith("\"Loud, \"\"fun\"\" car\""))
    }

    @Test fun trainLengthNotNoted() {
        val rear = Samples.ride("r", today, "09:00", position = 3, length = 3)
        val unknown = rear.withTrainLength(null)
        assertEquals(TrainPart.REAR, unknown.trainPart)
        assertNull(unknown.position)
        assertEquals(4, unknown.withTrainLength(4).position)
        assertNull(unknown.withTrainLength(4).trainPart)
        // A four-car train has no single middle car.
        val middle = rear.copy(position = 2).withTrainLength(null)
        assertEquals(TrainPart.MIDDLE, middle.trainPart)
        assertNull(middle.withTrainLength(4).position)
        assertEquals(2, middle.withTrainLength(3).position)
    }

    @Test fun backupKeepsNewFieldsAndReadsOldBackups() {
        val rides = listOf(
            Samples.ride("u", today, "09:00", length = 3).copy(trainLength = null, trainPart = TrainPart.REAR, placeInCar = PlaceInCar.BACK),
            Samples.ride("k", today, "10:00", position = 2, length = 4).copy(placeInCar = PlaceInCar.FRONT),
        )
        assertEquals(rides, Backup.fromJson(Backup.toJson(rides)))
        val old = """{"format": "trolleylog", "version": 1, "rides": [{"id": "o", "date": "2025-01-02", "boardedAt": "08:00"}]}"""
        assertEquals(3, Backup.fromJson(old).single().trainLength)
        val noLength = old.replace("\"version\": 1", "\"version\": 2")
        assertNull(Backup.fromJson(noLength).single().trainLength)
    }

    @Test fun spreadsheetImport() {
        val text = """
            Date,Boarded,Car,Car in train,Train length,Place in car,Boarded at,Disembarked at,Toward,Brochures,Note
            2024-10-21,08:24,4051B,rear,,back,Mission San Diego,SDSU,,Fall 2024,x16
            2025-08-28,17:39,4039A,rear,4,front,SDSU,Mission San Diego,,August 2025,"stadium, also at 17:40"
            2026-09-30,21:39,4020B,2nd,4,front,Old Town,Mission San Diego,,Fall 2026 | Otoño 2026,
            2024-11-26,17:26,5018A,middle,,,Mission San Diego,SDSU,El Cajon,,
            2025-11-10,,,,,back,Mission San Diego,SDSU,,,
            ,09:00,4001A,,,,,,,,
        """.trimIndent()
        val sheet = Backup.fromCsv(text)
        assertEquals(2, sheet.skipped)
        val (first, second, third, fourth) = sheet.rides
        assertEquals(TrainPart.REAR, first.trainPart)
        assertNull(first.trainLength)
        assertNull(first.position)
        assertEquals(PlaceInCar.BACK, first.placeInCar)
        assertEquals("msdS" to "sdsS", first.boardStation to first.exitStation)
        assertEquals("green", first.line)
        assertEquals(listOf("Fall 2024"), first.brochures)
        assertEquals("x16", first.note)
        assertEquals(4 to 4, second.position to second.trainLength)
        assertEquals("stadium, also at 17:40", second.note)
        assertEquals(2, third.position)
        assertEquals(listOf("Fall 2026", "Otoño 2026"), third.brochures)
        assertEquals(TrainPart.MIDDLE, fourth.trainPart)
        assertNull(fourth.placeInCar)
        // The same row always makes the same ride.
        assertEquals(first.id, Backup.fromCsv(text).rides.first().id)
        assertThrows(Backup.FormatException::class.java) { Backup.fromCsv("Car,Note\n4029B,hi") }
    }

    @Test fun spreadsheetRoundTrip() {
        val rides = Samples.history.take(4) + Samples.morning.copy(note = "Loud, \"fun\"\ncar") +
            Samples.ride("u", today, "21:39", car = "4020B", from = "oldS", to = "msdS", line = "green")
                .copy(trainLength = null, trainPart = TrainPart.REAR, placeInCar = PlaceInCar.BACK)
        val back = Backup.fromCsv(Backup.toCsv(rides)).rides
        assertEquals(rides.sortedWith(compareBy({ it.date }, { it.boardedAt })), back.map { r -> r.copy(id = rides.single { it.boardedAt == r.boardedAt && it.date == r.date }.id) })
    }

    @Test fun spreadsheetImportedTwiceAddsNothing() {
        val file = File.createTempFile("rides", ".json").apply { delete() }
        val store = RideStore(file)
        store.save(Samples.morning)
        val sheet = Backup.fromCsv(Backup.toCsv(Samples.history.take(3) + Samples.morning))
        assertEquals(RideStore.ImportResult(added = 3, alreadyHere = 1), store.import(sheet.rides))
        assertEquals(RideStore.ImportResult(added = 0, alreadyHere = 4), store.import(sheet.rides))
        file.delete()
    }

    @Test fun carPositionWithoutTrainLength() {
        val rides = listOf(
            Samples.ride("a", today, "09:00").copy(trainLength = null, trainPart = TrainPart.REAR, placeInCar = PlaceInCar.BACK),
            Samples.ride("b", today, "10:00").copy(trainLength = null, trainPart = TrainPart.FRONT, placeInCar = PlaceInCar.FRONT),
            Samples.ride("c", today, "11:00", position = 1, length = 2).copy(placeInCar = PlaceInCar.BACK),
        )
        val s = Stats.of(rides, today)
        assertEquals(mapOf(TrainPart.REAR to 1, TrainPart.FRONT to 1), s.parts)
        assertEquals(mapOf(2 to mapOf(1 to 1)), s.positions)
        assertEquals(mapOf(PlaceInCar.BACK to 2, PlaceInCar.FRONT to 1), s.places)
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

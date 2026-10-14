package org.nextcolor.trolleylog

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.nextcolor.trolleylog.data.Ride
import org.nextcolor.trolleylog.data.RideStore
import org.nextcolor.trolleylog.logic.Guess
import org.nextcolor.trolleylog.logic.Suggestions
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The ride being entered or changed. Nothing is saved until the editor's Save is pressed. */
data class Draft(
    val ride: Ride,
    val isNew: Boolean,
    val guess: Guess = Guess.NONE,
    /** Likely trips offered as one-tap choices, best first. */
    val trips: List<Pair<String, String>> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = RideStore(File(app.filesDir, "rides.json"))

    var draft by mutableStateOf<Draft?>(null)

    fun startRide(now: LocalDateTime = LocalDateTime.now()) {
        val s = Suggestions.forNewRide(store.rides.value, now)
        draft = Draft(
            Ride(
                id = UUID.randomUUID().toString(),
                date = now.toLocalDate(),
                boardedAt = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                position = s.position,
                trainLength = s.trainLength,
                boardStation = s.boardStation,
                exitStation = s.exitStation,
                line = s.line,
            ),
            isNew = true,
            guess = s.guess,
            trips = s.trips,
        )
    }

    fun edit(ride: Ride) {
        // Offer what would have been likely at the time of that ride.
        val others = store.rides.value.filter { it.id != ride.id }
        val trips = Suggestions.forNewRide(others, ride.date.atTime(ride.boardedAt)).trips
        draft = Draft(ride, isNew = false, trips = trips)
    }

    fun saveDraft() {
        draft?.let { store.save(it.ride.copy(car = it.ride.car.trim(), note = it.ride.note.trim())) }
        draft = null
    }

    fun deleteDraft() {
        draft?.let { store.delete(it.ride.id) }
        draft = null
    }

    fun markArrived(ride: Ride) {
        store.save(ride.copy(arrivedAt = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)))
    }
}

package org.nextcolor.trolleylog.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * One boarding. Only the date and boarding time are always known, everything
 * else can be left out and filled in later.
 */
data class Ride(
    val id: String,
    val date: LocalDate,
    val boardedAt: LocalTime,
    val arrivedAt: LocalTime? = null,
    /** Car number as painted, e.g. "4029B". Empty when not noted. */
    val car: String = "",
    /** Which car of the train, counted from the front, starting at 1. */
    val position: Int? = null,
    val trainLength: Int = DEFAULT_TRAIN_LENGTH,
    val boardStation: String? = null,
    val exitStation: String? = null,
    val line: String? = null,
    val brochures: List<String> = emptyList(),
    val note: String = "",
) {
    val carId: CarId? get() = CarId.parse(car)

    /** Rides past midnight count as arriving the next day. */
    val minutes: Long?
        get() = arrivedAt?.let {
            val m = Duration.between(boardedAt, it).toMinutes()
            if (m < 0) m + 24 * 60 else m
        }

    companion object {
        const val DEFAULT_TRAIN_LENGTH = 3
        const val MAX_TRAIN_LENGTH = 4
    }
}

package org.nextcolor.trolleylog.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Which car of the train, for rides whose train length was not noted. */
enum class TrainPart(val label: String) { FRONT("Front"), MIDDLE("Middle"), REAR("Rear") }

/** Where in the car the rider was. Not the A or B end, which is part of the car number. */
enum class PlaceInCar(val label: String) { FRONT("Front"), BACK("Back") }

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
    /** Which car of the train, counted from the front, starting at 1. Only set with a [trainLength]. */
    val position: Int? = null,
    /** Null when not noted. */
    val trainLength: Int? = DEFAULT_TRAIN_LENGTH,
    val boardStation: String? = null,
    val exitStation: String? = null,
    val line: String? = null,
    val brochures: List<String> = emptyList(),
    val note: String = "",
    /** Front, middle, or rear car when there is no [trainLength] to count [position] in. */
    val trainPart: TrainPart? = null,
    val placeInCar: PlaceInCar? = null,
) {
    val carId: CarId? get() = CarId.parse(car)

    /** Rides past midnight count as arriving the next day. */
    val minutes: Long?
        get() = arrivedAt?.let {
            val m = Duration.between(boardedAt, it).toMinutes()
            if (m < 0) m + 24 * 60 else m
        }

    /**
     * Changes the train length and keeps the car as close as it can: a
     * position becomes front, middle, or rear when the length is taken away,
     * and the other way round when one is given.
     */
    fun withTrainLength(length: Int?): Ride {
        if (length == trainLength) return this
        if (length == null) {
            val part = position?.let { p -> partOf(p, trainLength ?: return@let null) } ?: trainPart
            return copy(trainLength = null, position = null, trainPart = part)
        }
        val from = when {
            trainLength == null -> trainPart?.let { positionOf(it, length) }
            else -> position?.takeIf { it <= length }
        }
        return copy(trainLength = length, position = from, trainPart = null)
    }

    companion object {
        const val DEFAULT_TRAIN_LENGTH = 3
        const val MAX_TRAIN_LENGTH = 4

        fun partOf(position: Int, length: Int): TrainPart = when (position) {
            1 -> TrainPart.FRONT
            length -> TrainPart.REAR
            else -> TrainPart.MIDDLE
        }

        /** Null for the middle of a train that has no single middle car. */
        fun positionOf(part: TrainPart, length: Int): Int? = when (part) {
            TrainPart.FRONT -> 1
            TrainPart.REAR -> length
            TrainPart.MIDDLE -> if (length == 3) 2 else null
        }
    }
}

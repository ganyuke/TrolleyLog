package org.nextcolor.trolleylog.data

/** Body styles the wireframe knows how to draw. */
enum class CarShape { HIGH_FLOOR, LOW_FLOOR_LONG, LOW_FLOOR }

/** The San Diego Trolley fleet by number series. */
enum class CarSeries(
    val label: String,
    val numbers: IntRange,
    val built: String,
    val shape: CarShape,
    val lengthFeet: Int,
) {
    SD100("SD100", 2001..2052, "1993–95", CarShape.HIGH_FLOOR, 77),
    S70("S70", 3001..3011, "2004–05", CarShape.LOW_FLOOR_LONG, 90),
    S70US("S70 US", 4001..4065, "2011–13", CarShape.LOW_FLOOR, 81),
    S700("S700", 5001..5092, "2018 on", CarShape.LOW_FLOOR, 81);

    companion object {
        fun of(number: Int): CarSeries? = entries.firstOrNull { number in it.numbers }
    }
}

/** A car number as painted on the car, such as 4029B: the car and which end of it. */
data class CarId(val number: Int, val end: Char?) {
    val series: CarSeries? get() = CarSeries.of(number)

    override fun toString(): String = number.toString() + (end?.toString() ?: "")

    companion object {
        private val pattern = Regex("""^(\d{3,4})\s*([AB])?$""")

        fun parse(text: String): CarId? {
            val m = pattern.matchEntire(text.trim().uppercase()) ?: return null
            return CarId(m.groupValues[1].toInt(), m.groupValues[2].firstOrNull())
        }
    }
}

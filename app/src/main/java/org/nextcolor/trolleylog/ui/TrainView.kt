package org.nextcolor.trolleylog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nextcolor.trolleylog.data.CarShape
import org.nextcolor.trolleylog.data.Ride

/** Height of a car drawing as a fraction of its length, rail to raised pantograph. */
private const val CAR_ASPECT = 0.17f
/** Gap between coupled cars as a fraction of one car's length. */
private const val COUPLING_GAP = 0.035f

/**
 * Side view of the whole train, front on the left. Tapping a car picks it.
 * With [counts] the cars are shaded by how often each was ridden instead.
 */
@Composable
fun TrainStrip(
    length: Int,
    selected: Int?,
    shape: CarShape?,
    modifier: Modifier = Modifier,
    counts: Map<Int, Int>? = null,
    showLabels: Boolean = true,
    onSelect: ((Int) -> Unit)? = null,
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val units = length + (length - 1) * COUPLING_GAP
    val most = counts?.values?.maxOrNull() ?: 0
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(units / CAR_ASPECT)
                .semantics {
                    contentDescription = if (selected != null) "${carName(selected, length)} car of $length" else "Train of $length cars"
                }
                .pointerInput(length, onSelect) {
                    if (onSelect != null) detectTapGestures { tap ->
                        onSelect((tap.x / size.width * length).toInt().coerceIn(0, length - 1) + 1)
                    }
                }
        ) {
            val carWidth = size.width / units
            for (i in 0 until length) {
                val picked = selected == i + 1
                val fill = when {
                    counts != null && most > 0 ->
                        accent.copy(alpha = 0.08f + 0.6f * (counts[i + 1] ?: 0) / most)
                    picked -> accent.copy(alpha = 0.22f)
                    else -> null
                }
                drawCar(
                    shape = shape ?: CarShape.LOW_FLOOR,
                    origin = Offset(i * carWidth * (1 + COUPLING_GAP), 0f),
                    width = carWidth,
                    color = if (picked) accent else ink,
                    strokeWidth = (if (picked) 1.6f else 1.1f).dp.toPx(),
                    fill = fill,
                )
            }
            // Rail under the whole train.
            val railY = size.width / units * RAIL_Y
            drawLine(ink.copy(alpha = 0.5f), Offset(0f, railY), Offset(size.width, railY), 1.dp.toPx())
        }
        if (showLabels) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            for (i in 1..length) {
                val label = carName(i, length) + if (counts != null) " · ${counts[i] ?: 0}" else ""
                Text(
                    label,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected == i) accent else ink,
                )
            }
        }
    }
}

/** What riders call each car of a train: front, middle, rear. Longer trains count the ones in between. */
fun carName(position: Int, length: Int): String = when {
    length == 1 -> "Only"
    position == 1 -> "Front"
    position == length -> "Rear"
    length == 3 -> "Middle"
    else -> ordinal(position)
}

/** The car of [ride]'s train it was in, or null when not noted. */
fun carName(ride: Ride): String? = when {
    ride.position != null && ride.trainLength != null -> carName(ride.position, ride.trainLength)
    else -> ride.trainPart?.label
}

/** One car drawn large. Tapping a half picks the A or B end. */
@Composable
fun CarDetail(
    shape: CarShape?,
    end: Char?,
    modifier: Modifier = Modifier,
    onSelectEnd: ((Char) -> Unit)? = null,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f / CAR_ASPECT)
                .semantics { contentDescription = if (end != null) "Car, $end end marked" else "Car" }
                .pointerInput(onSelectEnd) {
                    if (onSelectEnd != null) detectTapGestures { tap ->
                        onSelectEnd(if (tap.x < size.width / 2) 'A' else 'B')
                    }
                }
        ) {
            drawCar(
                shape = shape ?: CarShape.LOW_FLOOR,
                origin = Offset.Zero,
                width = size.width,
                color = ink,
                strokeWidth = 1.4.dp.toPx(),
                fill = null,
                endFill = end?.let { it to accent.copy(alpha = 0.25f) },
                detailed = true,
            )
            val railY = size.width * RAIL_Y
            drawLine(ink.copy(alpha = 0.5f), Offset(0f, railY), Offset(size.width, railY), 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            for (e in listOf('A', 'B')) {
                Text(
                    "$e end",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (end == e) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Where things sit along a car, as fractions of its length. Proportions were
 * taken from side-on photos of cars 2034, 3007, 4034 and 5052.
 */
private class CarPlan(
    /** True for the SD100: flat upright ends, steps at the doors, bare roof. */
    val boxy: Boolean,
    /** Start and end of each articulation joint. */
    val joints: List<Pair<Float, Float>>,
    val trucks: List<Float>,
    /** Left edge of each double door. */
    val doors: List<Float>,
    /** Where the pantograph foot is bolted to the roof. */
    val pantograph: Float,
    /** Widest a single window pane gets before the stretch is split in two. */
    val paneWidth: Float,
)

private const val DOOR_WIDTH = 0.046f
private const val RAIL_Y = 0.163f

private fun planFor(shape: CarShape): CarPlan = when (shape) {
    // Two halves on three trucks. A door right behind each cab and one either
    // side of the joint, all with steps up to the high floor.
    CarShape.HIGH_FLOOR -> CarPlan(
        boxy = true,
        joints = listOf(0.488f to 0.512f),
        trucks = listOf(0.15f, 0.5f, 0.85f),
        doors = listOf(0.07f, 0.425f, 0.529f, 0.884f),
        pantograph = 0.455f,
        paneWidth = 0.075f,
    )
    // Three sections: A, a short centre section on its own truck, and B. Both
    // doors of each end section are in the low floor between truck and joint.
    // The 3000s are 9 ft longer, with the extra length in the end sections.
    CarShape.LOW_FLOOR_LONG -> CarPlan(
        boxy = false,
        joints = listOf(0.424f to 0.442f, 0.558f to 0.576f),
        trucks = listOf(0.125f, 0.5f, 0.875f),
        doors = listOf(0.218f, 0.348f, 0.606f, 0.736f),
        pantograph = 0.5f,
        paneWidth = 0.075f,
    )
    CarShape.LOW_FLOOR -> CarPlan(
        boxy = false,
        joints = listOf(0.412f to 0.432f, 0.568f to 0.588f),
        trucks = listOf(0.135f, 0.5f, 0.865f),
        doors = listOf(0.205f, 0.335f, 0.619f, 0.749f),
        pantograph = 0.5f,
        paneWidth = 0.085f,
    )
}

/**
 * Draws one car, A end on the left. All plan measurements are multiplied by
 * [width], so the same plan serves the small strip and the large detail view.
 * With [detailed] off, the fine lines that would smudge at small sizes are left out.
 */
private fun DrawScope.drawCar(
    shape: CarShape,
    origin: Offset,
    width: Float,
    color: Color,
    strokeWidth: Float,
    fill: Color?,
    endFill: Pair<Char, Color>? = null,
    detailed: Boolean = false,
) {
    val plan = planFor(shape)
    fun p(x: Float, y: Float) = Offset(origin.x + x * width, origin.y + y * width)
    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thin = Stroke(strokeWidth * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    val left = 0.012f
    val right = 1f - left
    val roofY = if (plan.boxy) 0.04f else 0.046f
    val floorY = if (plan.boxy) 0.132f else 0.142f
    val windowTop = if (plan.boxy) 0.054f else 0.063f
    val windowBottom = if (plan.boxy) 0.086f else 0.099f
    // How far back from the bumper the front of the roof starts.
    val rake = if (plan.boxy) 0.012f else 0.04f

    // One end's profile from the bumper up to the roof. The low-floor cars have
    // a raked, rounded windshield, the SD100 a nearly upright flat front.
    fun Path.end(mirror: Boolean, upward: Boolean) {
        fun m(x: Float) = if (mirror) 1f - x else x
        val bumperTop = p(m(left), floorY - 0.03f)
        val roofStart = p(m(left + rake), roofY)
        val bend = if (plan.boxy) p(m(left + 0.002f), roofY + 0.006f) else p(m(left + rake * 0.3f), roofY + 0.004f)
        if (upward) {
            lineTo(bumperTop)
            quadTo(bend, roofStart)
        } else {
            quadTo(bend, bumperTop)
        }
    }
    val body = Path().apply {
        moveTo(p(left, floorY))
        end(mirror = false, upward = true)
        lineTo(p(right - rake, roofY))
        end(mirror = true, upward = false)
        lineTo(p(right, floorY))
        close()
    }
    fill?.let { drawPath(body, it) }
    endFill?.let { (end, tint) ->
        val from = if (end == 'A') left else 0.5f
        val to = if (end == 'A') 0.5f else right
        clipPath(body) {
            drawRect(tint, p(from, roofY), Size((to - from) * width, (floorY - roofY) * width))
        }
    }
    drawPath(body, color, style = stroke)

    // Couplers.
    drawLine(color, p(0f, floorY - 0.012f), p(left, floorY - 0.012f), strokeWidth)
    drawLine(color, p(right, floorY - 0.012f), p(1f, floorY - 0.012f), strokeWidth)

    // Articulation bellows.
    for ((a, b) in plan.joints) {
        val folds = if (detailed) 4 else 2
        for (i in 0..folds) {
            val x = a + (b - a) * i / folds
            drawLine(color, p(x, roofY), p(x, floorY), thin.width)
        }
    }

    // Doors: two leaves, each with a tall narrow window. SD100 doors drop below the floor line to the steps.
    val doorTop = roofY + 0.01f
    val doorBottom = if (plan.boxy) floorY + 0.008f else floorY
    for (x in plan.doors) {
        drawRect(color, p(x, doorTop), Size(DOOR_WIDTH * width, (doorBottom - doorTop) * width), style = thin)
        drawLine(color, p(x + DOOR_WIDTH / 2, doorTop), p(x + DOOR_WIDTH / 2, doorBottom), thin.width)
        if (detailed) for (leaf in 0..1) {
            drawRoundRect(
                color,
                p(x + 0.006f + leaf * DOOR_WIDTH / 2, windowTop),
                Size((DOOR_WIDTH / 2 - 0.012f) * width, (windowBottom + 0.012f - windowTop) * width),
                CornerRadius(0.002f * width),
                style = thin,
            )
        }
    }

    // Windows fill the stretches between the cabs, the doors and the joints.
    val cabEnd = left + rake + if (plan.boxy) 0.036f else 0.03f
    val breaks = buildList {
        add(cabEnd)
        for (x in plan.doors) { add(x); add(x + DOOR_WIDTH) }
        for ((a, b) in plan.joints) { add(a); add(b) }
        add(1f - cabEnd)
    }.sorted()
    val pad = 0.008f
    for ((from, to) in breaks.chunked(2).map { it[0] to it[1] }) {
        val span = to - from - pad * 2
        if (span < 0.02f) continue
        drawRoundRect(
            color,
            p(from + pad, windowTop),
            Size(span * width, (windowBottom - windowTop) * width),
            CornerRadius(0.004f * width),
            style = thin,
        )
        // Long windows are several panes behind one frame.
        val panes = kotlin.math.ceil(span / plan.paneWidth).toInt()
        for (i in 1 until panes) {
            val x = from + pad + span * i / panes
            drawLine(color, p(x, windowTop), p(x, windowBottom), thin.width)
        }
    }

    // Cab side windows, with the leading edge following the windshield.
    for (mirror in listOf(false, true)) {
        fun m(x: Float) = if (mirror) 1f - x else x
        val lean = if (plan.boxy) 0.002f else 0.012f
        val front = left + rake + 0.004f
        val cab = Path().apply {
            moveTo(p(m(front - lean), windowBottom + 0.006f))
            lineTo(p(m(front), windowTop))
            lineTo(p(m(cabEnd - 0.004f), windowTop))
            lineTo(p(m(cabEnd - 0.004f), windowBottom + 0.006f))
            close()
        }
        drawPath(cab, color, style = thin)
    }

    // Trucks. The low-floor cars carry skirts that hide the top of the wheels.
    val wheelR = if (plan.boxy) 0.0125f else 0.0115f
    val wheelY = RAIL_Y - wheelR
    val axle = 0.026f
    for (x in plan.trucks) {
        clipRect(top = origin.y + floorY * width, bottom = origin.y + RAIL_Y * width + strokeWidth) {
            drawCircle(color, wheelR * width, p(x - axle, wheelY), style = thin)
            drawCircle(color, wheelR * width, p(x + axle, wheelY), style = thin)
        }
        drawLine(color, p(x - axle, wheelY + 0.002f), p(x + axle, wheelY + 0.002f), thin.width)
    }

    // Roof. Low-floor cars hide their equipment behind a long shroud on each
    // section. The SD100 has separate boxes.
    val sectionEdges = buildList {
        add(left + rake + 0.02f)
        for ((a, b) in plan.joints) { add(a - 0.006f); add(b + 0.006f) }
        add(right - rake - 0.02f)
    }
    val shroud = if (plan.boxy) 0.009f else 0.007f
    for ((a, b) in sectionEdges.chunked(2).map { it[0] to it[1] }) {
        if (plan.boxy) {
            val third = (b - a) / 3
            drawRect(color, p(a + third * 0.4f, roofY - shroud), Size(third * width, shroud * width), style = thin)
            drawRect(color, p(a + third * 1.7f, roofY - shroud), Size(third * 0.9f * width, shroud * width), style = thin)
        } else {
            val cover = Path().apply {
                moveTo(p(a, roofY))
                lineTo(p(a + 0.01f, roofY - shroud))
                lineTo(p(b - 0.01f, roofY - shroud))
                lineTo(p(b, roofY))
            }
            drawPath(cover, color, style = thin)
        }
    }

    // Single-arm pantograph: lower arm leans one way, upper arm folds back to the wire.
    val px = plan.pantograph
    val top = 0.004f
    val arm = Path().apply {
        moveTo(p(px + 0.022f, roofY - shroud))
        lineTo(p(px - 0.02f, (roofY - shroud + top) / 2 + 0.003f))
        lineTo(p(px + 0.006f, top))
    }
    drawPath(arm, color, style = thin)
    drawLine(color, p(px - 0.012f, top), p(px + 0.024f, top), strokeWidth)
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)
private fun Path.quadTo(c: Offset, o: Offset) = quadraticTo(c.x, c.y, o.x, o.y)

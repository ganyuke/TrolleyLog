package org.nextcolor.trolleylog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nextcolor.trolleylog.data.Network
import org.nextcolor.trolleylog.data.Station
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.tan

private const val MAX_TILE_ZOOM = 19
private const val ATTRIBUTION = "© OpenStreetMap contributors"

/**
 * Turns the light street map into a dark one: brightness is flipped while the
 * hues stay put, so parks remain green and water blue. Then it is dimmed a little.
 */
private val nightTiles = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.47f, -1.17f, -0.12f, 0f, 225f,
            -0.35f, -0.35f, -0.12f, 0f, 225f,
            -0.35f, -1.17f, 0.70f, 0f, 225f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

// Lines that share track are drawn side by side instead of on top of each other.
private val SIDE_SHIFT_DP = mapOf("blue" to -4f, "orange" to 0f, "green" to 4f, "copper" to 0f)

/** Web Mercator, the projection the map tiles use. The whole world is 1 unit wide and tall. */
private fun worldX(lon: Double) = (lon + 180.0) / 360.0
private fun worldY(lat: Double): Double {
    val r = Math.toRadians(lat)
    return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2
}

private class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val centreX get() = (left + right) / 2
    val centreY get() = (top + bottom) / 2
}

private fun boxAround(stations: List<Station>) = Box(
    stations.minOf { worldX(it.lon) }, stations.minOf { worldY(it.lat) },
    stations.maxOf { worldX(it.lon) }, stations.maxOf { worldY(it.lat) },
)

private val network = boxAround(Network.stations)

/** What part of the world is on screen: the world point at the centre, and pixels per world unit. */
private data class Camera(val x: Double, val y: Double, val scale: Double)

/**
 * A street map with the Trolley lines and stations on top. Pinch to zoom,
 * drag to move, tap a station to mark it. Station names appear as there is room.
 */
@Composable
fun StationMap(
    boardStation: String?,
    exitStation: String?,
    marked: String?,
    onMark: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val measurer = rememberTextMeasurer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tiles = remember { MapTiles(context.cacheDir, scope) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var canvas by remember { mutableStateOf(Size.Zero) }

    fun Camera.toScreen(wx: Double, wy: Double) = Offset(
        (canvas.width / 2 + (wx - x) * scale).toFloat(),
        (canvas.height / 2 + (wy - y) * scale).toFloat(),
    )
    fun Camera.toScreen(s: Station) = toScreen(worldX(s.lon), worldY(s.lat))

    /** Zooms by [factor] while keeping the world point under [anchor] where it is, then drags by [drag]. */
    fun Camera.moved(anchor: Offset, factor: Float, drag: Offset, density: Float): Camera {
        val widest = minOf(canvas.width, canvas.height) * 0.7 / maxOf(network.right - network.left, network.bottom - network.top)
        val closest = 256.0 * density * (1 shl MAX_TILE_ZOOM)
        val next = (scale * factor).coerceIn(widest, closest)
        val ax = x + (anchor.x - canvas.width / 2) / scale
        val ay = y + (anchor.y - canvas.height / 2) / scale
        return Camera(
            ax - (anchor.x + drag.x - canvas.width / 2) / next,
            ay - (anchor.y + drag.y - canvas.height / 2) / next,
            next,
        )
    }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { centroid, drag, pinch, _ ->
                    camera = camera?.moved(centroid, pinch, drag, density)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap -> camera = camera?.moved(tap, 2.5f, Offset.Zero, density) },
                    onTap = { tap ->
                        val cam = camera ?: return@detectTapGestures
                        Network.stations
                            .map { it to (cam.toScreen(it) - tap).getDistance() }
                            .filter { it.second < 28.dp.toPx() }
                            .minByOrNull { it.second }
                            ?.let { onMark(it.first.id) }
                    },
                )
            }
    ) {
        canvas = size
        // Open on the stations already chosen, or on the whole network.
        val cam = camera ?: run {
            val chosen = listOfNotNull(Network.station(boardStation), Network.station(exitStation))
            val box = if (chosen.size == 2) boxAround(chosen) else network
            val margin = 56.dp.toPx()
            val fit = minOf(
                (size.width - 2 * margin) / (box.right - box.left).coerceAtLeast(1e-9),
                (size.height - 2 * margin) / (box.bottom - box.top).coerceAtLeast(1e-9),
            )
            Camera(box.centreX, box.centreY, fit.coerceAtMost(256.0 * density * (1 shl 15))).also { camera = it }
        }
        tiles.revision.intValue // Draw again when a tile arrives.

        clipRect {
            // Street map. Tiles are drawn a little under their full size on
            // dense screens, which trades some sharpness for readable street names.
            val tilePx = 256 * density
            val z = floor(log2(cam.scale / tilePx) + 0.75).toInt().coerceIn(0, MAX_TILE_ZOOM)
            val filter = if (dark) nightTiles else null
            val count = 1 shl z
            val drawn = cam.scale / count
            val leftWorld = cam.x - size.width / 2 / cam.scale
            val topWorld = cam.y - size.height / 2 / cam.scale
            val firstX = floor(leftWorld * count).toInt()
            val firstY = floor(topWorld * count).toInt()
            val lastX = floor((leftWorld + size.width / cam.scale) * count).toInt()
            val lastY = floor((topWorld + size.height / cam.scale) * count).toInt()
            for (tx in firstX..lastX) for (ty in firstY..lastY) {
                if (tx !in 0 until count || ty !in 0 until count) continue
                val at = cam.toScreen(tx.toDouble() / count, ty.toDouble() / count)
                val dst = IntOffset(at.x.roundToInt(), at.y.roundToInt())
                val end = cam.toScreen((tx + 1.0) / count, (ty + 1.0) / count)
                val dstSize = IntSize(end.x.roundToInt() - dst.x, end.y.roundToInt() - dst.y)
                val tile = tiles.get(z, tx, ty)
                if (tile != null) {
                    drawImage(tile, dstOffset = dst, dstSize = dstSize, colorFilter = filter)
                    continue
                }
                // Until the tile arrives, stretch the matching corner of a coarser one.
                for (up in 1..4) {
                    if (z - up < 0) break
                    val parent = tiles.peek(z - up, tx shr up, ty shr up) ?: continue
                    val part = parent.width shr up
                    val mask = (1 shl up) - 1
                    drawImage(
                        parent,
                        srcOffset = IntOffset((tx and mask) * part, (ty and mask) * part),
                        srcSize = IntSize(part, part),
                        dstOffset = dst,
                        dstSize = dstSize,
                        colorFilter = filter,
                    )
                    break
                }
            }

            // Lines, each with a pale edge so it stands out from the streets.
            for (pass in 0..1) for (line in Network.lines) {
                val shift = (SIDE_SHIFT_DP[line.id] ?: 0f).dp.toPx()
                val pts = (0 until line.path.size / 2).map {
                    cam.toScreen(worldX(line.path[it * 2 + 1].toDouble()), worldY(line.path[it * 2].toDouble()))
                }
                val path = Path()
                pts.forEachIndexed { i, pt ->
                    val before = pts[maxOf(i - 1, 0)]
                    val after = pts[minOf(i + 1, pts.lastIndex)]
                    val len = hypot(after.x - before.x, after.y - before.y).coerceAtLeast(0.001f)
                    val x = pt.x - (after.y - before.y) / len * shift
                    val y = pt.y + (after.x - before.x) / len * shift
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val width = if (pass == 0) 7.dp.toPx() else 4.dp.toPx()
                drawPath(
                    path,
                    if (pass == 0) scheme.surface else Color(line.color),
                    style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            val ends = Network.lines.flatMap { listOf(it.stationIds.first(), it.stationIds.last()) }.toSet()
            fun importance(s: Station) = when (s.id) {
                marked -> 0
                boardStation, exitStation -> 1
                in ends -> 2
                else -> if (Network.linesAt(s.id).size > 1) 3 else 4
            }
            // Dots first, so that no name is ever placed over another station.
            val margin = 40.dp.toPx()
            val shown = Network.stations.sortedBy(::importance).filter {
                val at = cam.toScreen(it)
                at.x > -margin && at.y > -margin && at.x < size.width + margin && at.y < size.height + margin
            }
            fun radius(s: Station) =
                (if (s.id == boardStation || s.id == exitStation || s.id == marked) 8 else 5).dp.toPx()
            val placed = ArrayList<Rect>()
            for (s in shown.asReversed()) {
                val at = cam.toScreen(s)
                val chosen = s.id == boardStation || s.id == exitStation
                val r = radius(s)
                if (s.id == marked) drawCircle(scheme.primary.copy(alpha = 0.3f), r * 2.2f, at)
                drawCircle(if (chosen) scheme.primary else scheme.surface, r, at)
                drawCircle(if (chosen || s.id == marked) scheme.primary else scheme.onSurface, r, at, style = Stroke(2.dp.toPx()))
                placed += Rect(at.x - r, at.y - r, at.x + r, at.y + r)
            }
            val credit = measurer.measure(ATTRIBUTION, TextStyle(fontSize = 10.sp, color = scheme.onSurfaceVariant))
            val creditAt = Offset(8.dp.toPx(), size.height - credit.size.height - 8.dp.toPx())
            drawRoundRect(
                scheme.surface.copy(alpha = 0.8f),
                Offset(creditAt.x - 4.dp.toPx(), creditAt.y - 2.dp.toPx()),
                Size(credit.size.width + 8.dp.toPx(), credit.size.height + 4.dp.toPx()),
                CornerRadius(4.dp.toPx()),
            )
            drawText(credit, topLeft = creditAt)
            placed += Rect(creditAt, Size(credit.size.width.toFloat(), credit.size.height.toFloat())).inflate(6.dp.toPx())
            val padX = 6.dp.toPx()
            val padY = 2.dp.toPx()
            for (s in shown) {
                val at = cam.toScreen(s)
                val r = radius(s)
                val text = when (s.id) {
                    boardStation -> "${s.name} · board"
                    exitStation -> "${s.name} · disembark"
                    else -> s.name
                }
                val layout = measurer.measure(
                    text,
                    TextStyle(
                        fontSize = 12.sp,
                        fontWeight = if (importance(s) < 2) FontWeight.Bold else FontWeight.Medium,
                        color = scheme.onSurface,
                    ),
                )
                val w = layout.size.width + padX * 2
                val h = layout.size.height + padY * 2
                // Try the right of the dot first, then left, below and above.
                val spots = listOf(
                    Offset(at.x + r + 4, at.y - h / 2),
                    Offset(at.x - r - 4 - w, at.y - h / 2),
                    Offset(at.x - w / 2, at.y + r + 4),
                    Offset(at.x - w / 2, at.y - r - 4 - h),
                )
                val spot = spots.firstOrNull { o ->
                    val box = Rect(o, Size(w, h)).inflate(2f)
                    box.left >= 0 && box.right <= size.width && placed.none(box::overlaps)
                } ?: continue
                placed += Rect(spot, Size(w, h))
                drawRoundRect(scheme.surface.copy(alpha = 0.88f), spot, Size(w, h), CornerRadius(h / 2))
                drawText(layout, topLeft = Offset(spot.x + padX, spot.y + padY))
            }

        }
    }
}

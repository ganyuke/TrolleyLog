package org.nextcolor.trolleylog.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Street map tiles from OpenStreetMap. Tiles are kept on disk after the first
 * download, so places seen before also show offline. Without a connection the
 * missing tiles are simply left blank.
 */
class MapTiles(private val cacheDir: File, private val scope: CoroutineScope) {
    /** Bumped whenever a tile arrives, so the map knows to draw again. */
    val revision = mutableIntStateOf(0)

    private val memory = LruCache<String, ImageBitmap>(80)
    private val loading = HashSet<String>()
    private val failedAt = HashMap<String, Long>()
    private val downloads = Semaphore(6)

    /** The tile if it is ready. Otherwise starts fetching it and returns null. */
    fun get(z: Int, x: Int, y: Int): ImageBitmap? {
        val key = key(z, x, y)
        memory.get(key)?.let { return it }
        val failed = failedAt[key]
        if (key !in loading && (failed == null || System.currentTimeMillis() - failed > RETRY_AFTER_MS)) {
            loading += key
            pending.incrementAndGet()
            scope.launch {
                val image = withContext(Dispatchers.IO) { downloads.withPermit { load(z, x, y) } }
                loading -= key
                if (image != null) memory.put(key, image) else failedAt[key] = System.currentTimeMillis()
                pending.decrementAndGet()
                revision.intValue++
            }
        }
        return null
    }

    /** A tile only if it is already in memory. Used to stand in for a finer tile that is still loading. */
    fun peek(z: Int, x: Int, y: Int): ImageBitmap? = memory.get(key(z, x, y))

    private fun key(z: Int, x: Int, y: Int) = "$z/$x/$y"

    private fun load(z: Int, x: Int, y: Int): ImageBitmap? = try {
        val file = File(cacheDir, "map/${key(z, x, y)}.png")
        if (!file.exists()) {
            val connection = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "TrolleyLog/1.0.0 (org.nextcolor.trolleylog)")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val bytes = connection.inputStream.use { it.readBytes() }
            file.parentFile?.mkdirs()
            val part = File(file.path + ".part")
            part.writeBytes(bytes)
            part.renameTo(file)
        }
        BitmapFactory.decodeFile(file.path)?.asImageBitmap() ?: run { file.delete(); null }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val RETRY_AFTER_MS = 20_000L
        /** Tiles being fetched right now, across all maps. */
        val pending = AtomicInteger()
    }
}

package org.nextcolor.trolleylog.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Keeps all rides in memory and mirrors them to one JSON file, in the same
 * format as a backup. Writes go to a temp file first so a crash mid-write
 * cannot leave a half-written log.
 */
class RideStore(private val file: File) {
    private val state = MutableStateFlow(load())
    val rides: StateFlow<List<Ride>> = state

    data class ImportResult(val added: Int, val alreadyHere: Int)

    private fun load(): List<Ride> =
        if (file.exists()) runCatching { Backup.fromJson(file.readText()) }.getOrDefault(emptyList())
        else emptyList()

    @Synchronized
    private fun update(rides: List<Ride>) {
        val sorted = rides.sortedWith(compareBy({ it.date }, { it.boardedAt }))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Backup.toJson(sorted))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        state.value = sorted
    }

    fun save(ride: Ride) = update(state.value.filter { it.id != ride.id } + ride)

    fun delete(id: String) = update(state.value.filter { it.id != id })

    /** Adds the rides that are not in the log yet. Rides already here are left as they are. */
    fun import(incoming: List<Ride>): ImportResult {
        val known = state.value.mapTo(HashSet()) { it.id }
        val fresh = incoming.filter { known.add(it.id) }
        if (fresh.isNotEmpty()) update(state.value + fresh)
        return ImportResult(fresh.size, incoming.size - fresh.size)
    }
}

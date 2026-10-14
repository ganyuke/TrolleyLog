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
    data class ReplaceResult(val added: Int, val replaced: Int, val unchanged: Int)

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

    /**
     * Adds the rides that are not in the log yet. Rides already here are left
     * as they are. A ride counts as already here by its id, or when one on
     * the same date has the same boarding time and car.
     */
    fun import(incoming: List<Ride>): ImportResult {
        val known = state.value.mapTo(HashSet()) { it.id }
        val trips = state.value.mapTo(HashSet(), ::sameRideKey)
        val fresh = incoming.filter { known.add(it.id) and trips.add(sameRideKey(it)) }
        if (fresh.isNotEmpty()) update(state.value + fresh)
        return ImportResult(fresh.size, incoming.size - fresh.size)
    }

    /**
     * Adds the rides that are not in the log yet and replaces the ones that
     * are with the incoming version, matched as in [import]. A replaced ride
     * keeps its id. Rides that are not in [incoming] are left as they are.
     */
    fun replace(incoming: List<Ride>): ReplaceResult {
        val byId = state.value.associateBy { it.id }
        val byTrip = state.value.associateBy(::sameRideKey)
        val result = state.value.associateByTo(LinkedHashMap()) { it.id }
        var added = 0
        var replaced = 0
        var unchanged = 0
        val seen = HashSet<String>()
        for (ride in incoming) {
            val old = byId[ride.id] ?: byTrip[sameRideKey(ride)]
            val id = old?.id ?: ride.id
            // A second row for a ride already handled from this file is not counted again.
            if (!seen.add(id)) continue
            val new = ride.copy(id = id)
            when {
                old == null -> added++
                old == new -> unchanged++
                else -> replaced++
            }
            result[id] = new
        }
        if (added + replaced > 0) update(result.values.toList())
        return ReplaceResult(added, replaced, unchanged)
    }

    private fun sameRideKey(r: Ride) = Triple(r.date, r.boardedAt, r.car.uppercase())
}

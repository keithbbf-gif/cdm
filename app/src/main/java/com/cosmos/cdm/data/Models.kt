package com.cosmos.cdm.data

import org.json.JSONArray
import org.json.JSONObject

data class Panel<T>(
    val data: T? = null,
    val measuredAtMs: Long? = null,
    val error: String? = null,
)

enum class ConnKind { Idle, Connecting, Connected, Partial, Offline, Unauthorized }

data class ConnState(
    val kind: ConnKind = ConnKind.Idle,
    val message: String = "not connected",
    val baseUrl: String = "",
)

data class StatusSnapshot(
    val ready: Boolean,
    val root: String?,
    val treeId: String?,
    val ledgerSeq: String,
    val ledgerEvent: String?,
)

data class HealthRow(
    val name: String,
    val ok: Boolean?,
    val detail: String,
)

data class HealthSnapshot(
    val verdict: String,
    val diagnosis: String?,
    val reds: Int?,
    val negativeControlRed: Boolean?,
    val rows: List<HealthRow>,
)

data class SpendRail(
    val name: String,
    val capUsd: Double?,
    val settledUsd: Double,
    val reservedUsd: Double,
    val headroomUsd: Double?,
    val unpricedCalls: Int,
    val expiresInDays: Double?,
    val expiryRisk: String?,
)

data class SpendSnapshot(
    val rails: List<SpendRail>,
)

data class JobsSnapshot(
    val total: Int,
    val byState: Map<String, List<String>>,
)

data class Maker(
    val id: String,
    val kind: String,
    val location: String,
    val function: String,
    val access: String,
    val potentialSources: List<String>,
    val tags: List<String>,
)

data class LedgerEvent(
    val seq: Long?,
    val event: String,
    val writer: String,
    val tMs: Long?,
    // Client-side monotonic id assigned when the event is appended to the feed.
    // Stable and unique even when seq is null — the LazyColumn key. (seq alone
    // is not usable: null seqs collapsed onto hashCode(), which collides.)
    val localId: Long = 0L,
)

data class CommandEntry(
    val tsMs: Long,
    val kind: Kind,
    val head: String,
    val body: String?,
) {
    enum class Kind { CMD, OK, ERR }
}

fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val v = optDouble(key, Double.NaN)
    return if (v.isFinite()) v else null
}

fun JSONObject.optBoolOrNull(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return try {
        getBoolean(key)
    } catch (_: Exception) {
        null
    }
}

fun toEpochMs(v: Double): Long = if (v > 1e12) v.toLong() else (v * 1000.0).toLong()

/**
 * The server's own measurement time, or null when the response does not carry
 * one. Null means age UNKNOWN — never substitute the client clock, which would
 * report a fresh-looking age for data the server never timestamped (same rule
 * as cDeck).
 */
fun extractMeasuredAtMs(obj: JSONObject): Long? {
    for (key in listOf("measured_at_epoch", "measured_at", "served_at")) {
        val v = obj.optDoubleOrNull(key) ?: continue
        return toEpochMs(v)
    }
    return null
}

fun parseStatus(obj: JSONObject): StatusSnapshot {
    val head = obj.optJSONObject("ledger_head") ?: JSONObject()
    val seq = when {
        head.has("seq") && !head.isNull("seq") -> head.opt("seq").toString()
        else -> "—"
    }
    return StatusSnapshot(
        ready = obj.optBoolean("ready", false),
        root = obj.optString("root").ifBlank { null },
        treeId = obj.optString("tree_id").ifBlank { null },
        ledgerSeq = seq,
        ledgerEvent = head.optString("event").ifBlank { null },
    )
}

fun parseHealth(obj: JSONObject): HealthSnapshot {
    val rowsObj = obj.optJSONObject("rows") ?: JSONObject()
    val rows = mutableListOf<HealthRow>()
    val keys = rowsObj.keys()
    while (keys.hasNext()) {
        val name = keys.next()
        val r = rowsObj.optJSONObject(name) ?: JSONObject()
        rows.add(
            HealthRow(
                name = name,
                ok = r.optBoolOrNull("ok"),
                detail = r.optString("detail", ""),
            ),
        )
    }
    rows.sortBy { it.name }
    return HealthSnapshot(
        verdict = obj.optString("verdict", "UNKNOWN"),
        diagnosis = obj.optString("diagnosis").ifBlank { null },
        reds = if (obj.has("reds") && !obj.isNull("reds")) obj.optInt("reds") else null,
        negativeControlRed = obj.optBoolOrNull("negative_control_red"),
        rows = rows,
    )
}

fun parseSpend(obj: JSONObject): SpendSnapshot {
    val railsObj = obj.optJSONObject("rails") ?: JSONObject()
    val rails = mutableListOf<SpendRail>()
    val keys = railsObj.keys()
    while (keys.hasNext()) {
        val name = keys.next()
        val r = railsObj.optJSONObject(name) ?: JSONObject()
        rails.add(
            SpendRail(
                name = name,
                capUsd = r.optDoubleOrNull("cap_usd"),
                settledUsd = r.optDoubleOrNull("settled_usd") ?: 0.0,
                reservedUsd = r.optDoubleOrNull("reserved_usd") ?: 0.0,
                headroomUsd = r.optDoubleOrNull("headroom_usd"),
                unpricedCalls = r.optInt("unpriced_calls", 0),
                expiresInDays = r.optDoubleOrNull("expires_in_days"),
                expiryRisk = r.optString("expiry_risk").ifBlank { null },
            ),
        )
    }
    rails.sortBy { it.name }
    return SpendSnapshot(rails)
}

fun parseJobs(obj: JSONObject): JobsSnapshot {
    val jobsObj = obj.optJSONObject("jobs") ?: JSONObject()
    val byState = linkedMapOf<String, MutableList<String>>()
    val keys = jobsObj.keys()
    var total = 0
    while (keys.hasNext()) {
        val id = keys.next()
        val st = jobsObj.opt(id)?.toString() ?: "UNKNOWN"
        byState.getOrPut(st) { mutableListOf() }.add(id)
        total++
    }
    val sorted = byState.toSortedMap()
    return JobsSnapshot(total = total, byState = sorted)
}

fun parseMakers(obj: JSONObject): List<Maker> {
    val arr = obj.optJSONArray("makers") ?: JSONArray()
    val out = ArrayList<Maker>(arr.length())
    for (i in 0 until arr.length()) {
        val m = arr.optJSONObject(i) ?: continue
        out.add(
            Maker(
                id = m.optString("id", "—"),
                kind = m.optString("kind", "OTHER"),
                location = m.optString("location", "—"),
                function = m.optString("function", ""),
                access = m.optString("access", "—"),
                potentialSources = stringList(m.optJSONArray("potential_sources")),
                tags = stringList(m.optJSONArray("tags")),
            ),
        )
    }
    return out
}

fun parseEvents(obj: JSONObject): Pair<Long?, List<LedgerEvent>> {
    val head = if (obj.has("head_seq") && !obj.isNull("head_seq")) obj.optLong("head_seq") else null
    val arr = obj.optJSONArray("events") ?: JSONArray()
    val out = ArrayList<LedgerEvent>(arr.length())
    for (i in 0 until arr.length()) {
        val e = arr.optJSONObject(i) ?: continue
        val t = e.optDoubleOrNull("t")
        out.add(
            LedgerEvent(
                seq = if (e.has("seq") && !e.isNull("seq")) e.optLong("seq") else null,
                event = e.optString("event", "—"),
                writer = e.optString("writer", "—"),
                tMs = t?.let { toEpochMs(it) },
            ),
        )
    }
    return head to out
}

fun JSONObject.pretty(): String = try {
    toString(2)
} catch (_: Exception) {
    toString()
}

private fun stringList(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    val out = ArrayList<String>(arr.length())
    for (i in 0 until arr.length()) {
        val s = arr.optString(i, "")
        if (s.isNotBlank()) out.add(s)
    }
    return out
}

fun humanAge(ageMs: Long): String {
    var sec = ageMs / 1000
    if (sec < 0) sec = 0
    return when {
        sec < 60 -> "${sec}s"
        sec < 3600 -> "${sec / 60}m ${sec % 60}s"
        sec < 86400 -> "${sec / 3600}h ${(sec % 3600) / 60}m"
        else -> "${sec / 86400}d ${(sec % 86400) / 3600}h"
    }
}

fun usd(v: Double?): String {
    if (v == null || !v.isFinite()) return "—"
    return "$" + "%.2f".format(v)
}

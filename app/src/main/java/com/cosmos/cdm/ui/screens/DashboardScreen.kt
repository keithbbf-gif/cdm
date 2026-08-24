package com.cosmos.cdm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmos.cdm.data.ConnKind
import com.cosmos.cdm.data.HealthSnapshot
import com.cosmos.cdm.data.JobsSnapshot
import com.cosmos.cdm.data.LedgerEvent
import com.cosmos.cdm.data.SpendRail
import com.cosmos.cdm.data.SpendSnapshot
import com.cosmos.cdm.data.StatusSnapshot
import com.cosmos.cdm.data.humanAge
import com.cosmos.cdm.data.usd
import com.cosmos.cdm.ui.CdmViewModel
import com.cosmos.cdm.ui.components.EmptyLine
import com.cosmos.cdm.ui.components.Kv
import com.cosmos.cdm.ui.components.PanelCard
import com.cosmos.cdm.ui.components.Pill
import com.cosmos.cdm.ui.theme.CosmosAmber
import com.cosmos.cdm.ui.theme.CosmosCyan
import com.cosmos.cdm.ui.theme.CosmosGreen
import com.cosmos.cdm.ui.theme.CosmosHead
import com.cosmos.cdm.ui.theme.CosmosInk
import com.cosmos.cdm.ui.theme.CosmosInkFaint
import com.cosmos.cdm.ui.theme.CosmosInput
import com.cosmos.cdm.ui.theme.CosmosPanelEdge
import com.cosmos.cdm.ui.theme.CosmosRed

@Composable
fun DashboardScreen(vm: CdmViewModel, modifier: Modifier = Modifier) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val nowMs by vm.nowMs.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val spend by vm.spend.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()

    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        if (conn.kind == ConnKind.Offline || conn.kind == ConnKind.Unauthorized) {
            item {
                Text(
                    conn.message,
                    color = CosmosRed,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A1212), RoundedCornerShape(6.dp))
                        .border(1.dp, CosmosRed, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                )
            }
        }
        item {
            PanelCard("STATUS", status.measuredAtMs, nowMs, status.error) {
                val d = status.data
                if (d == null) {
                    EmptyLine("not connected — set the COSMOS URL in Settings")
                } else {
                    StatusBody(d)
                }
            }
        }
        item {
            PanelCard("HEALTH", health.measuredAtMs, nowMs, health.error) {
                val d = health.data
                if (d == null) EmptyLine("not connected") else HealthBody(d)
            }
        }
        item {
            PanelCard("SPEND", spend.measuredAtMs, nowMs, spend.error) {
                val d = spend.data
                if (d == null) EmptyLine("not connected") else SpendBody(d)
            }
        }
        item {
            PanelCard("JOBS", jobs.measuredAtMs, nowMs, jobs.error) {
                val d = jobs.data
                if (d == null) EmptyLine("not connected") else JobsBody(d)
            }
        }
        item {
            PanelCard("LIVE EVENTS", events.measuredAtMs, nowMs, events.error) {
                val feed = events.data.orEmpty()
                if (feed.isEmpty()) {
                    EmptyLine("no events yet — the ledger tail streams here")
                }
            }
        }
        // localId is assigned once per appended event in the VM — stable and
        // unique even when seq is null (seq ?: hashCode collided on
        // identical null-seq rows).
        items(events.data.orEmpty(), key = { ev -> ev.localId }) { ev ->
            EventRow(ev, nowMs)
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun StatusBody(d: StatusSnapshot) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill(if (d.ready) "READY" else "NOT READY", if (d.ready) CosmosGreen else CosmosRed)
    }
    Kv("tree_id", d.treeId ?: "—", CosmosCyan)
    Kv("ledger head", "seq ${d.ledgerSeq} · ${d.ledgerEvent ?: "—"}")
    Kv("root", d.root ?: "—")
}

@Composable
private fun HealthBody(d: HealthSnapshot) {
    val vu = d.verdict.uppercase()
    val vColor = when {
        vu == "GREEN" -> CosmosGreen
        vu.startsWith("BOARD-BROKEN") || vu.startsWith("RED") -> CosmosRed
        else -> CosmosAmber
    }
    Text(
        vu,
        color = vColor,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        fontSize = 20.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    if (!d.diagnosis.isNullOrBlank()) {
        Text(
            d.diagnosis,
            color = CosmosAmber,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    if (d.rows.isEmpty()) {
        EmptyLine("no health rows reported")
    } else {
        d.rows.forEach { row ->
            val isNc = row.name.contains("negative", ignoreCase = true)
            val color = when {
                row.ok == true -> CosmosGreen
                row.ok == false && !isNc -> CosmosRed
                else -> CosmosAmber
            }
            val mark = when {
                row.ok == true -> "●"
                row.ok == false -> "●"
                else -> "?"
            }
            Column(Modifier.padding(vertical = 3.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mark, color = color, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.name,
                        color = CosmosInk,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.detail.isNotBlank()) {
                    Text(
                        row.detail,
                        color = if (row.ok == false && !isNc) CosmosRed else CosmosInkFaint,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 18.dp),
                    )
                }
            }
        }
    }
    Kv("reds", d.reds?.toString() ?: "—")
    val nc = when (d.negativeControlRed) {
        true -> "RED as designed" to CosmosGreen
        false -> "NOT RED — checker may be incapable of failing" to CosmosRed
        null -> "—" to CosmosInkFaint
    }
    Kv("negative control", nc.first, nc.second)
}

@Composable
private fun SpendBody(d: SpendSnapshot) {
    if (d.rails.isEmpty()) {
        EmptyLine("no rails reported")
        return
    }
    Text(
        "bar vs cap: settled / reserved · headroom at/under 0 is RED",
        color = CosmosInkFaint,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    d.rails.forEach { RailBlock(it) }
}

@Composable
private fun RailBlock(r: SpendRail) {
    val under = r.headroomUsd != null && r.headroomUsd <= 0
    val low = !under && r.capUsd != null && r.capUsd > 0 && r.headroomUsd != null &&
        r.headroomUsd / r.capUsd < 0.1
    val headColor = when {
        under -> CosmosRed
        low -> CosmosAmber
        else -> CosmosGreen
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                r.name,
                color = CosmosHead,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (under) Pill("UNDER THRESHOLD", CosmosRed)
            else if (low) Pill("LOW HEADROOM", CosmosAmber)
        }
        if (r.unpricedCalls > 0) {
            Text("${r.unpricedCalls} UNPRICED", color = CosmosAmber, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        if (!r.expiryRisk.isNullOrBlank()) {
            Text("EXPIRY RISK: ${r.expiryRisk}", color = CosmosAmber, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        SpendBar(r, under)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Meta("settled", usd(r.settledUsd))
            Meta("reserved", usd(r.reservedUsd))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Meta("cap", usd(r.capUsd))
            Text(
                "headroom ${usd(r.headroomUsd)}",
                color = headColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        r.expiresInDays?.let {
            Text(
                "expires in ${it}d",
                color = if (r.expiryRisk != null) CosmosAmber else CosmosInkFaint,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Meta(k: String, v: String) {
    Text("$k $v", color = CosmosInkFaint, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}

@Composable
private fun SpendBar(r: SpendRail, under: Boolean) {
    val cap = r.capUsd
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(12.dp)
            .background(CosmosInput, RoundedCornerShape(3.dp))
            .border(1.dp, CosmosPanelEdge, RoundedCornerShape(3.dp)),
    ) {
        if (cap != null && cap > 0) {
            val sPct = (r.settledUsd / cap).toFloat().coerceIn(0f, 1f)
            val rPct = (r.reservedUsd / cap).toFloat().coerceIn(0f, 1f - sPct)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth
                if (under) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(CosmosRed.copy(alpha = 0.18f), RoundedCornerShape(3.dp)),
                    )
                }
                Box(
                    Modifier
                        .width(w * sPct)
                        .fillMaxHeight()
                        .background(CosmosCyan, RoundedCornerShape(3.dp))
                        .align(Alignment.CenterStart),
                )
                Box(
                    Modifier
                        .width(w * rPct)
                        .fillMaxHeight()
                        .offset(x = w * sPct)
                        .background(CosmosAmber)
                        .align(Alignment.CenterStart),
                )
            }
        }
    }
}

@Composable
private fun JobsBody(d: JobsSnapshot) {
    if (d.total == 0) {
        EmptyLine("no jobs yet")
        return
    }
    Kv("total", d.total.toString(), CosmosHead)
    d.byState.forEach { (st, ids) ->
        val color = stateColor(st)
        Column(Modifier.padding(vertical = 6.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    st,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text("×${ids.size}", color = CosmosInkFaint, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            Text(
                ids.joinToString(" · "),
                color = CosmosInk,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
    }
}

private fun stateColor(st: String): Color {
    val s = st.uppercase()
    return when {
        Regex("FAIL|ERROR|DEAD|BROKE").containsMatchIn(s) -> CosmosRed
        Regex("WAIT|PEND|QUEUE|HOLD").containsMatchIn(s) -> CosmosAmber
        Regex("RUN|ACTIVE|LIVE|DONE|OK|COMPLETE|SUCC").containsMatchIn(s) -> CosmosGreen
        else -> CosmosInk
    }
}

@Composable
private fun EventRow(ev: LedgerEvent, nowMs: Long) {
    val age = ev.tMs?.let { humanAge(nowMs - it) } ?: "—"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            ev.seq?.toString() ?: "—",
            color = CosmosInkFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.width(40.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                ev.event,
                color = CosmosInk,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(ev.writer, color = CosmosCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Text(age, color = CosmosInkFaint, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

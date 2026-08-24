package com.cosmos.cdm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmos.cdm.data.Maker
import com.cosmos.cdm.ui.CdmViewModel
import com.cosmos.cdm.ui.components.CdmButton
import com.cosmos.cdm.ui.components.EmptyLine
import com.cosmos.cdm.ui.components.Kv
import com.cosmos.cdm.ui.components.PanelCard
import com.cosmos.cdm.ui.components.Pill
import com.cosmos.cdm.ui.theme.CosmosCyan
import com.cosmos.cdm.ui.theme.CosmosHead
import com.cosmos.cdm.ui.theme.CosmosInk
import com.cosmos.cdm.ui.theme.CosmosInkFaint
import com.cosmos.cdm.ui.theme.CosmosPanel
import com.cosmos.cdm.ui.theme.CosmosPanelEdge

private val KINDS = listOf("AGENT", "TOOL", "CONNECTOR", "SKILL")

@Composable
fun CreateScreen(vm: CdmViewModel, modifier: Modifier = Modifier) {
    val makers by vm.makers.collectAsStateWithLifecycle()
    val nowMs by vm.nowMs.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf<String?>(null) }
    val all = makers.data.orEmpty()
    val counts = all.groupingBy { it.kind }.eachCount()
    val shown = if (kind == null) all else all.filter { it.kind.equals(kind, ignoreCase = true) }

    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            PanelCard("CREATE", makers.measuredAtMs, nowMs, makers.error) {
                Text(
                    "where Agent / Tool / Connector / Skill can be made — from GET /makers",
                    color = CosmosInkFaint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                KINDS.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { k ->
                            val on = kind == k
                            CdmButton(
                                label = "$k ${counts[k] ?: 0}",
                                onClick = { kind = if (kind == k) null else k },
                                modifier = Modifier.weight(1f),
                                tint = if (on) CosmosCyan else CosmosInk,
                            )
                        }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyLine(
                    if (kind == null) "no makers registered"
                    else "no makers of kind $kind — none registered, not an error",
                )
            }
        }
        items(shown, key = { it.id }) { m ->
            MakerCard(m)
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MakerCard(m: Maker) {
    var open by remember(m.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(CosmosPanel, RoundedCornerShape(8.dp))
            .border(1.dp, CosmosPanelEdge, RoundedCornerShape(8.dp))
            .clickable { open = !open }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.id,
                color = CosmosHead,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Pill(m.kind, CosmosCyan)
        }
        if (m.function.isNotBlank()) {
            Text(
                m.function,
                color = CosmosInkFaint,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (m.tags.isNotEmpty()) {
            Text(
                m.tags.joinToString(" · "),
                color = CosmosInkFaint,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Kv("where", m.location)
            Kv("do", m.function.ifBlank { "—" })
            Kv("how", m.access)
            Kv("sources", m.potentialSources.joinToString(" · ").ifBlank { "—" })
        } else {
            Text(
                "tap to open maker",
                color = CosmosInkFaint,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

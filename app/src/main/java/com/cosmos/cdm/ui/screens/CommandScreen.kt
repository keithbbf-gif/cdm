package com.cosmos.cdm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmos.cdm.data.CommandEntry
import com.cosmos.cdm.ui.CdmViewModel
import com.cosmos.cdm.ui.components.CdmButton
import com.cosmos.cdm.ui.components.CdmField
import com.cosmos.cdm.ui.components.EmptyLine
import com.cosmos.cdm.ui.theme.CosmosAmber
import com.cosmos.cdm.ui.theme.CosmosCyan
import com.cosmos.cdm.ui.theme.CosmosGreen
import com.cosmos.cdm.ui.theme.CosmosInk
import com.cosmos.cdm.ui.theme.CosmosInkFaint
import com.cosmos.cdm.ui.theme.CosmosInput
import com.cosmos.cdm.ui.theme.CosmosPanel
import com.cosmos.cdm.ui.theme.CosmosPanelEdge

private val QUICK = listOf("status", "audit", "jobs", "help")

@Composable
fun CommandScreen(vm: CdmViewModel, modifier: Modifier = Modifier) {
    val log by vm.commands.collectAsStateWithLifecycle()
    val busy by vm.commandBusy.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    fun send() {
        // UI-level courtesy only — the authoritative guard is the atomic
        // busy CAS in CdmViewModel.runCommand. This just keeps the typed
        // text from being cleared when a submit races a running command.
        if (busy) return
        val t = text
        text = ""
        vm.runCommand(t)
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        Text(
            "POST /api/v1/command",
            color = CosmosInkFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(">", color = CosmosCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            CdmField(
                value = text,
                onValueChange = { text = it },
                placeholder = "try: help",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { send() }),
            )
        }
        Spacer(Modifier.height(8.dp))
        CdmButton(
            label = if (busy) "RUNNING…" else "RUN",
            onClick = { send() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            tint = CosmosCyan,
        )
        Spacer(Modifier.height(8.dp))
        QUICK.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { cmd ->
                    CdmButton(
                        cmd,
                        onClick = { vm.runCommand(cmd) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(CosmosInput, RoundedCornerShape(6.dp))
                .border(1.dp, CosmosPanelEdge, RoundedCornerShape(6.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (log.isEmpty()) {
                EmptyLine("no commands run yet — results appear here")
            } else {
                log.forEach { entry -> CommandRow(entry) }
            }
        }
    }
}

@Composable
private fun CommandRow(e: CommandEntry) {
    val color = when (e.kind) {
        CommandEntry.Kind.CMD -> CosmosCyan
        CommandEntry.Kind.OK -> CosmosGreen
        CommandEntry.Kind.ERR -> CosmosAmber
    }
    val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(e.tsMs))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .border(width = 0.dp, color = CosmosPanel, shape = RoundedCornerShape(0.dp))
            .padding(start = 8.dp),
    ) {
        Row {
            Text(ts, color = CosmosInkFaint, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            if (e.head.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    e.head,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp,
                )
            }
        }
        e.body?.let {
            Text(it, color = if (e.kind == CommandEntry.Kind.CMD) CosmosCyan else CosmosInk, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
    }
}

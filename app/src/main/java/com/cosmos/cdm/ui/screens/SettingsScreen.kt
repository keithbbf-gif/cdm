package com.cosmos.cdm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmos.cdm.data.ConnKind
import com.cosmos.cdm.ui.CdmViewModel
import com.cosmos.cdm.ui.components.CdmButton
import com.cosmos.cdm.ui.components.CdmField
import com.cosmos.cdm.ui.theme.CosmosAmber
import com.cosmos.cdm.ui.theme.CosmosCyan
import com.cosmos.cdm.ui.theme.CosmosGreen
import com.cosmos.cdm.ui.theme.CosmosHead
import com.cosmos.cdm.ui.theme.CosmosInkFaint
import com.cosmos.cdm.ui.theme.CosmosRed

@Composable
fun SettingsScreen(vm: CdmViewModel, modifier: Modifier = Modifier) {
    val url by vm.serverUrl.collectAsStateWithLifecycle()
    val bearer by vm.bearer.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()

    val connColor = when (conn.kind) {
        ConnKind.Connected -> CosmosGreen
        ConnKind.Offline, ConnKind.Unauthorized -> CosmosRed
        ConnKind.Connecting -> CosmosAmber
        ConnKind.Idle -> CosmosInkFaint
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text(
            "SERVER",
            color = CosmosHead,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "COSMOS HTTP API — LAN or Tailscale. Persisted on this device.",
            color = CosmosInkFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        CdmField(
            value = url,
            onValueChange = { vm.setServerUrl(it) },
            placeholder = "http://host:8791",
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CdmButton("LAN", onClick = { vm.applyLanPreset() }, modifier = Modifier.weight(1f))
            CdmButton(
                "TAILSCALE",
                onClick = { vm.applyTailscalePreset() },
                modifier = Modifier.weight(1f),
                tint = CosmosCyan,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "BEARER",
            color = CosmosHead,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Optional. Session memory only — NEVER written to disk. Leave blank when COSMOS is running --no-auth.",
            color = CosmosAmber,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        CdmField(
            value = bearer,
            onValueChange = { vm.setBearer(it) },
            placeholder = "paste bearer (kept in memory only)",
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(16.dp))
        CdmButton(
            label = if (conn.kind == ConnKind.Idle) "CONNECT" else "RECONNECT",
            onClick = { vm.connect() },
            modifier = Modifier.fillMaxWidth(),
            tint = CosmosCyan,
        )
        Spacer(Modifier.height(8.dp))
        CdmButton(
            label = "DISCONNECT",
            onClick = { vm.disconnect() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            conn.message,
            color = connColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "cDm is the phone-sized cDeck. Same COSMOS HTTP API as VMC. No secrets belong in this app or its APK.",
            color = CosmosInkFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

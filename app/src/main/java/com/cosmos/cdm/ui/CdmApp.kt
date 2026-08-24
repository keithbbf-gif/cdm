package com.cosmos.cdm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmos.cdm.data.ConnKind
import com.cosmos.cdm.ui.screens.CommandScreen
import com.cosmos.cdm.ui.screens.CreateScreen
import com.cosmos.cdm.ui.screens.DashboardScreen
import com.cosmos.cdm.ui.screens.SettingsScreen
import com.cosmos.cdm.ui.theme.CosmosAmber
import com.cosmos.cdm.ui.theme.CosmosBg
import com.cosmos.cdm.ui.theme.CosmosCyan
import com.cosmos.cdm.ui.theme.CosmosGreen
import com.cosmos.cdm.ui.theme.CosmosHead
import com.cosmos.cdm.ui.theme.CosmosInk
import com.cosmos.cdm.ui.theme.CosmosInkFaint
import com.cosmos.cdm.ui.theme.CosmosPanel
import com.cosmos.cdm.ui.theme.CosmosRed

private enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Dash", Icons.Filled.Home),
    Command("Command", Icons.Filled.PlayArrow),
    Create("CREATE", Icons.Filled.Add),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun CdmApp(vm: CdmViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Dashboard.name) }
    val current = Tab.valueOf(tab)
    val conn by vm.conn.collectAsStateWithLifecycle()
    val connColor = when (conn.kind) {
        ConnKind.Connected -> CosmosGreen
        ConnKind.Offline, ConnKind.Unauthorized -> CosmosRed
        ConnKind.Partial, ConnKind.Connecting -> CosmosAmber
        ConnKind.Idle -> CosmosInkFaint
    }

    Scaffold(
        containerColor = CosmosBg,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "cDm",
                        color = CosmosHead,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontSize = 18.sp,
                    )
                    Text(
                        "  COSMOS",
                        color = CosmosCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                    )
                }
                Text(
                    when (conn.kind) {
                        ConnKind.Connected -> "LIVE"
                        ConnKind.Partial -> "PARTIAL"
                        ConnKind.Offline -> "OFFLINE"
                        ConnKind.Unauthorized -> "AUTH"
                        ConnKind.Connecting -> "…"
                        ConnKind.Idle -> "IDLE"
                    },
                    color = connColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 12.sp,
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = CosmosPanel, contentColor = CosmosInk) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = current == t,
                        onClick = { tab = t.name },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = {
                            Text(t.label, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CosmosCyan,
                            selectedTextColor = CosmosCyan,
                            unselectedIconColor = CosmosInkFaint,
                            unselectedTextColor = CosmosInkFaint,
                            indicatorColor = CosmosBg,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (current) {
            Tab.Dashboard -> DashboardScreen(vm, m)
            Tab.Command -> CommandScreen(vm, m)
            Tab.Create -> CreateScreen(vm, m)
            Tab.Settings -> SettingsScreen(vm, m)
        }
    }
}

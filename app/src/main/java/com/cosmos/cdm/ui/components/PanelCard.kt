package com.cosmos.cdm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmos.cdm.data.humanAge
import com.cosmos.cdm.ui.theme.CosmosAmber
import com.cosmos.cdm.ui.theme.CosmosCyan
import com.cosmos.cdm.ui.theme.CosmosGreen
import com.cosmos.cdm.ui.theme.CosmosHead
import com.cosmos.cdm.ui.theme.CosmosInk
import com.cosmos.cdm.ui.theme.CosmosInkFaint
import com.cosmos.cdm.ui.theme.CosmosInput
import com.cosmos.cdm.ui.theme.CosmosPanel
import com.cosmos.cdm.ui.theme.CosmosPanelEdge
import com.cosmos.cdm.ui.theme.CosmosRed

private val CardShape = RoundedCornerShape(8.dp)
private const val STALE_MS = 30_000L

@Composable
fun PanelCard(
    title: String,
    measuredAtMs: Long?,
    nowMs: Long,
    error: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stale = measuredAtMs != null && nowMs - measuredAtMs > STALE_MS
    val border = when {
        error != null -> CosmosRed
        stale -> CosmosAmber
        else -> CosmosPanelEdge
    }
    val ageColor = when {
        measuredAtMs == null -> CosmosInkFaint
        error != null -> CosmosRed
        stale -> CosmosAmber
        else -> CosmosGreen
    }
    val ageText = if (measuredAtMs == null) {
        "no data"
    } else {
        "measured ${humanAge(nowMs - measuredAtMs)} ago"
    }
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, border, CardShape)
            .background(CosmosPanel, CardShape)
            .padding(bottom = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = CosmosHead,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                fontSize = 13.sp,
            )
            Text(
                ageText,
                color = ageColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        Column(Modifier.padding(horizontal = 12.dp)) {
            if (error != null) {
                Text(
                    if (error == "offline") {
                        "OFFLINE — COSMOS is not answering. Last good data stays with its age."
                    } else {
                        "UNREACHABLE — $error"
                    },
                    color = CosmosRed,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CosmosRed, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .padding(bottom = 4.dp),
                )
            }
            content()
        }
    }
}

@Composable
fun EmptyLine(text: String) {
    Text(text, color = CosmosInkFaint, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
}

@Composable
fun Kv(label: String, value: String, valueColor: Color = CosmosInk) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = CosmosInkFaint, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
fun CdmButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CosmosInk,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CosmosInput,
            contentColor = tint,
            disabledContainerColor = CosmosInput,
            disabledContentColor = CosmosInkFaint,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, CosmosPanelEdge),
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun CdmField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        placeholder = {
            Text(placeholder, color = CosmosInkFaint, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
        },
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = CosmosInk,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CosmosInput,
            unfocusedContainerColor = CosmosInput,
            disabledContainerColor = CosmosInput,
            focusedTextColor = CosmosInk,
            unfocusedTextColor = CosmosInk,
            cursorColor = CosmosCyan,
            focusedIndicatorColor = CosmosCyan,
            unfocusedIndicatorColor = CosmosPanelEdge,
        ),
        shape = RoundedCornerShape(6.dp),
    )
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        fontSize = 12.sp,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

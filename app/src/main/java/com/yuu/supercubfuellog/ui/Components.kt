package com.yuu.supercubfuellog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ScreenContainer(content: @Composable ColumnScope.() -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF0B0F1A), Color(0xFF111827), Color(0xFF0B0F1A)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFF8FAFC), Color(0xFFFFFFFF), Color(0xFFF1F5F9)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            content = content
        )
    }
}

@Composable
fun BottomSettingsButton(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    enabled: Boolean = true
) {
    if (currentRoute == "settings") return

    OutlinedButton(
        enabled = enabled,
        onClick = { onNavigate("settings") },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        androidx.compose.material3.Icon(Icons.Filled.Settings, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("設定")
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun NavButtons(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    enabled: Boolean = true
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 600.dp
        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NavButton(label = "記録", icon = Icons.Filled.Edit, isActive = currentRoute == "record", onClick = { onNavigate("record") }, enabled = enabled)
                NavButton(label = "履歴・インポート", icon = Icons.Filled.List, isActive = currentRoute == "history", onClick = { onNavigate("history") }, enabled = enabled)
                NavButton(label = "月次レポート", icon = Icons.Filled.CalendarMonth, isActive = currentRoute == "monthly", onClick = { onNavigate("monthly") }, enabled = enabled)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavButton(label = "記録", icon = Icons.Filled.Edit, isActive = currentRoute == "record", onClick = { onNavigate("record") }, modifier = Modifier.weight(1f), enabled = enabled)
                NavButton(label = "履歴・インポート", icon = Icons.Filled.List, isActive = currentRoute == "history", onClick = { onNavigate("history") }, modifier = Modifier.weight(1f), enabled = enabled)
                NavButton(label = "月次レポート", icon = Icons.Filled.CalendarMonth, isActive = currentRoute == "monthly", onClick = { onNavigate("monthly") }, modifier = Modifier.weight(1f), enabled = enabled)
            }
        }
    }
}

@Composable
private fun NavButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = if (isActive) {
        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    } else {
        ButtonDefaults.outlinedButtonColors()
    }
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val buttonModifier = modifier.fillMaxWidth().height(48.dp)

    if (isActive) {
        Button(onClick = onClick, enabled = enabled, colors = colors, modifier = buttonModifier) {
            androidx.compose.material3.Icon(icon, null, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = contentColor)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, colors = colors, modifier = buttonModifier) {
            androidx.compose.material3.Icon(icon, null, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = contentColor)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

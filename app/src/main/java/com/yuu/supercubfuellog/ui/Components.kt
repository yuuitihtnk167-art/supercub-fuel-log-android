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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseUser
import com.yuu.supercubfuellog.data.DataSource

@Composable
fun ScreenContainer(content: @Composable ColumnScope.() -> Unit) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
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
fun ScreenHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    user: FirebaseUser?,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
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
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (user == null) {
                OutlinedButton(onClick = onSignInClick, modifier = Modifier.height(40.dp)) {
                    Text("Googleログイン")
                }
            } else {
                Text(
                    text = user.displayName ?: "ログイン中",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onSignOutClick) {
                    Text("ログアウト")
                }
            }
        }
    }
}

@Composable
fun NavButtons(currentRoute: String, onNavigate: (String) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 600.dp
        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NavButton(
                    label = "記録",
                    icon = Icons.Filled.Edit,
                    isActive = currentRoute == "record",
                    onClick = { onNavigate("record") }
                )
                NavButton(
                    label = "履歴・インポート",
                    icon = Icons.Filled.List,
                    isActive = currentRoute == "history",
                    onClick = { onNavigate("history") }
                )
                NavButton(
                    label = "月次レポート",
                    icon = Icons.Filled.CalendarMonth,
                    isActive = currentRoute == "monthly",
                    onClick = { onNavigate("monthly") }
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavButton(
                    label = "記録",
                    icon = Icons.Filled.Edit,
                    isActive = currentRoute == "record",
                    onClick = { onNavigate("record") },
                    modifier = Modifier.weight(1f)
                )
                NavButton(
                    label = "履歴・インポート",
                    icon = Icons.Filled.List,
                    isActive = currentRoute == "history",
                    onClick = { onNavigate("history") },
                    modifier = Modifier.weight(1f)
                )
                NavButton(
                    label = "月次レポート",
                    icon = Icons.Filled.CalendarMonth,
                    isActive = currentRoute == "monthly",
                    onClick = { onNavigate("monthly") },
                    modifier = Modifier.weight(1f)
                )
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
    modifier: Modifier = Modifier
) {
    val colors = if (isActive) {
        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    } else {
        ButtonDefaults.outlinedButtonColors()
    }
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val buttonModifier = modifier.fillMaxWidth().height(48.dp)

    if (isActive) {
        Button(onClick = onClick, colors = colors, modifier = buttonModifier) {
            androidx.compose.material3.Icon(icon, null, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = contentColor)
        }
    } else {
        OutlinedButton(onClick = onClick, colors = colors, modifier = buttonModifier) {
            androidx.compose.material3.Icon(icon, null, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = contentColor)
        }
    }
}

@Composable
fun DataSourceToggle(selected: DataSource, onSelect: (DataSource) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected == DataSource.LOCAL) {
            Button(onClick = { onSelect(DataSource.LOCAL) }, modifier = Modifier.height(40.dp)) {
                Text("ローカル")
            }
        } else {
            OutlinedButton(onClick = { onSelect(DataSource.LOCAL) }, modifier = Modifier.height(40.dp)) {
                Text("ローカル")
            }
        }

        if (selected == DataSource.CLOUD) {
            Button(onClick = { onSelect(DataSource.CLOUD) }, modifier = Modifier.height(40.dp)) {
                Text("クラウド")
            }
        } else {
            OutlinedButton(onClick = { onSelect(DataSource.CLOUD) }, modifier = Modifier.height(40.dp)) {
                Text("クラウド")
            }
        }
    }
}

@Composable
fun TargetSelectDialog(
    title: String,
    message: String,
    onSelect: (DataSource) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = { onSelect(DataSource.LOCAL) }) {
                Text("ローカル")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = { onSelect(DataSource.CLOUD) }) {
                    Text("クラウド")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
            }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
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

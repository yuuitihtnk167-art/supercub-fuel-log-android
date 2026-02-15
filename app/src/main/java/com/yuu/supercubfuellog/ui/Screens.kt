package com.yuu.supercubfuellog.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuu.supercubfuellog.MainViewModel
import com.yuu.supercubfuellog.data.FuelRecord
import com.yuu.supercubfuellog.util.CsvUtils
import com.yuu.supercubfuellog.util.DateUtils
import com.yuu.supercubfuellog.util.FormulaUtils
import com.yuu.supercubfuellog.util.MonthlyCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecordScreen(
    viewModel: MainViewModel,
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current

    val editingRecord = viewModel.editingRecord
    var date by rememberSaveable { mutableStateOf("") }
    var mileage by rememberSaveable { mutableStateOf("") }
    var fuel by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(editingRecord) {
        if (editingRecord != null) {
            date = editingRecord.date
            mileage = editingRecord.mileage?.toString() ?: ""
            fuel = editingRecord.fuel.toString()
        } else {
            date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            mileage = ""
            fuel = ""
        }
    }

    ScreenContainer {
        ScreenHeader(
            title = "スーパー・カブ燃費記録",
            subtitle = "給油記録をシンプルに管理します。",
            icon = Icons.Filled.Edit
        )

        Spacer(modifier = Modifier.height(16.dp))
        NavButtons(currentRoute = currentRoute, onNavigate = onNavigate)

        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (editingRecord != null) "給油記録を編集" else "新しい給油記録",
                    style = MaterialTheme.typography.titleLarge
                )

                val openDatePicker = {
                    val initialDate = runCatching {
                        LocalDate.parse(date, DateTimeFormatter.ISO_DATE)
                    }.getOrElse { LocalDate.now() }

                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            date = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                        },
                        initialDate.year,
                        initialDate.monthValue - 1,
                        initialDate.dayOfMonth
                    ).show()
                }

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("日付 (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = openDatePicker) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = "Select date"
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = mileage,
                    onValueChange = { mileage = it },
                    label = { Text("走行距離 (km) 任意") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = fuel,
                    onValueChange = { fuel = it },
                    label = { Text("給油量 (L)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val fuelNum = fuel.toDoubleOrNull()
                            val mileageNum = mileage.takeIf { it.isNotBlank() }?.toDoubleOrNull()

                            if (date.isBlank() || fuelNum == null || fuelNum <= 0) {
                                viewModel.postMessage("日付と給油量を正しく入力してください。")
                                return@Button
                            }

                            if (mileage.isNotBlank() && mileageNum == null) {
                                viewModel.postMessage("走行距離を正しい数値で入力してください。")
                                return@Button
                            }

                            val now = System.currentTimeMillis()
                            val record = FuelRecord(
                                id = editingRecord?.id ?: now.toString(),
                                date = date,
                                mileage = mileageNum,
                                fuel = fuelNum,
                                fuelEfficiency = editingRecord?.fuelEfficiency,
                                isEstimated = false,
                                lastUpdated = now
                            )
                            viewModel.saveRecord(record, com.yuu.supercubfuellog.data.DataSource.LOCAL)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (editingRecord != null) "更新" else "保存")
                    }

                    if (editingRecord != null) {
                        OutlinedButton(
                            onClick = { viewModel.cancelEditing() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("キャンセル")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onEditNavigate: (FuelRecord) -> Unit
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingDeleteAll by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var expandedFormulaId by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = readTextFromUri(context, uri)
            val parsed = CsvUtils.parseCsv(text)
            if (parsed.isEmpty()) {
                viewModel.postMessage("CSVファイルに有効なデータがありません。")
            } else {
                val now = System.currentTimeMillis()
                val imported = parsed.mapIndexed { index, csv ->
                    FuelRecord(
                        id = "${now}_$index",
                        date = csv.date,
                        mileage = csv.mileage,
                        fuel = csv.fuel,
                        lastUpdated = now
                    )
                }
                viewModel.importRecords(imported, com.yuu.supercubfuellog.data.DataSource.LOCAL)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val csv = CsvUtils.generateCsv(records)
            writeTextToUri(context, uri, csv)
            viewModel.postMessage("CSVをエクスポートしました。")
        }
    }

    if (pendingDeleteAll) {
        ConfirmDialog(
            title = "全件削除",
            message = "すべての記録を削除しますか？",
            confirmLabel = "削除する",
            onConfirm = {
                viewModel.deleteAll()
                pendingDeleteAll = false
            },
            onDismiss = { pendingDeleteAll = false }
        )
    }

    if (pendingDeleteId != null) {
        ConfirmDialog(
            title = "記録を削除",
            message = "この記録を削除しますか？",
            confirmLabel = "削除する",
            onConfirm = {
                viewModel.deleteRecord(pendingDeleteId!!)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null }
        )
    }

    ScreenContainer {
        ScreenHeader(
            title = "履歴・インポート",
            subtitle = "給油履歴の確認とCSVの入出力",
            icon = Icons.Filled.List
        )

        Spacer(modifier = Modifier.height(16.dp))
        NavButtons(currentRoute = currentRoute, onNavigate = onNavigate)

        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CSVインポート・エクスポート", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "CSVには「日付」「給油量」列が必要です。「走行距離」は任意です。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/*")) }) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CSVインポート")
                    }
                    OutlinedButton(onClick = { exportLauncher.launch("fuel_records.csv") }) {
                        Text("CSVエクスポート")
                    }
                }
                OutlinedButton(onClick = { pendingDeleteAll = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("すべて削除")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("給油履歴", style = MaterialTheme.typography.titleMedium)

                if (records.isEmpty()) {
                    Text(
                        text = "まだ記録がありません。記録ページから追加してください。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val sortedDesc = records.sortedByDescending { it.date }
                    sortedDesc.forEach { record ->
                        val formulaInfo = FormulaUtils.getFormulaInfo(records, record)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(record.date, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "走行距離: ${record.mileage?.let { formatDouble(it) } ?: "未記録"} km | 給油量: ${formatDouble(record.fuel)} L",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (record.isEstimated) {
                                    Text("推定", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                                }
                                record.fuelEfficiency?.let {
                                    Text(
                                        text = "燃費: ${formatDouble(it)} km/L",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (formulaInfo != null) {
                                        OutlinedButton(onClick = {
                                            expandedFormulaId = if (expandedFormulaId == record.id) null else record.id
                                        }) {
                                            Icon(Icons.Filled.Calculate, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (expandedFormulaId == record.id) "計算式を隠す" else "計算式")
                                        }
                                    }
                                    OutlinedButton(onClick = { onEditNavigate(record) }) {
                                        Text("編集")
                                    }
                                    OutlinedButton(onClick = { pendingDeleteId = record.id }) {
                                        Text("削除")
                                    }
                                }

                                if (expandedFormulaId == record.id && formulaInfo != null) {
                                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("燃費計算", style = MaterialTheme.typography.titleSmall)
                                        Text("前回走行距離: ${formatDouble(formulaInfo.prevMileage)} km (${formulaInfo.prevDate})", style = MaterialTheme.typography.bodySmall)
                                        Text("今回走行距離: ${formatDouble(formulaInfo.currentMileage)} km", style = MaterialTheme.typography.bodySmall)
                                        Text("走行距離の増加: ${formatDouble(formulaInfo.distance)} km", style = MaterialTheme.typography.bodySmall)
                                        Text("今回の給油: ${formatDouble(formulaInfo.currentFuel)} L", style = MaterialTheme.typography.bodySmall)
                                        if (formulaInfo.intermediateFuels.isNotEmpty()) {
                                            Text("途中の給油:", style = MaterialTheme.typography.bodySmall)
                                            formulaInfo.intermediateFuels.forEach { fuelInfo ->
                                                Text(
                                                    text = "${fuelInfo.date}: ${formatDouble(fuelInfo.fuel)} L${if (fuelInfo.isEstimated) " (推定)" else ""}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        Text("合計給油量: ${formatDouble(formulaInfo.totalFuel)} L", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            text = "${formatDouble(formulaInfo.distance)} km × ${formatDouble(formulaInfo.totalFuel)} L = ${formatDouble(formulaInfo.efficiency)} km/L",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val lastUpdated = records.mapNotNull { it.lastUpdated }.maxOrNull()
        val lastUpdatedText = DateUtils.formatDateTime(lastUpdated)
        if (lastUpdatedText != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "最終更新: $lastUpdatedText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MonthlyScreen(
    viewModel: MainViewModel,
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val records by viewModel.records.collectAsStateWithLifecycle()

    val months = remember(records) {
        records.mapNotNull { DateUtils.toYearMonth(it.date) }.distinct().sortedDescending()
    }
    var selectedMonth by rememberSaveable { mutableStateOf(months.firstOrNull() ?: "") }
    var showFormula by remember { mutableStateOf(false) }

    LaunchedEffect(months) {
        if (months.isNotEmpty() && selectedMonth !in months) {
            selectedMonth = months.first()
        }
    }

    val stats = if (selectedMonth.isNotBlank()) {
        MonthlyCalculator.calculate(records, selectedMonth)
    } else null

    ScreenContainer {
        ScreenHeader(
            title = "月次燃費レポート",
            subtitle = "選択した月の燃費を確認できます。",
            icon = Icons.Filled.CalendarMonth
        )

        Spacer(modifier = Modifier.height(16.dp))
        NavButtons(currentRoute = currentRoute, onNavigate = onNavigate)

        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("月を選択", style = MaterialTheme.typography.titleMedium)

                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedMonth,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true },
                        trailingIcon = {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        months.forEach { month ->
                            DropdownMenuItem(
                                text = { Text(month) },
                                onClick = {
                                    selectedMonth = month
                                    expanded = false
                                    showFormula = false
                                }
                            )
                        }
                    }
                }

                if (stats?.averageEfficiency != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("平均燃費", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = formatDouble(stats.averageEfficiency),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("km/L", style = MaterialTheme.typography.bodyMedium)

                            Text("総走行距離: ${formatDouble(stats.totalMileage)} km", style = MaterialTheme.typography.bodySmall)
                            Text("総給油量: ${formatDouble(stats.totalFuel)} L", style = MaterialTheme.typography.bodySmall)
                            Text("${stats.records.size} 件", style = MaterialTheme.typography.bodySmall)

                            OutlinedButton(onClick = { showFormula = !showFormula }) {
                                Text(if (showFormula) "計算式を隠す" else "計算式を表示")
                            }

                            if (showFormula) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    stats.previousMonthLastDate?.let {
                                        Text("前月最終走行距離: ${formatDouble(stats.previousMonthLastMileage ?: 0.0)} km ($it)", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (stats.isFirstRecordEver) {
                                        Text("データ内の最初の記録です。最初の走行距離の給油は除外しています。", style = MaterialTheme.typography.bodySmall)
                                    }
                                    val first = stats.recordsWithMileage.firstOrNull()
                                    val last = stats.recordsWithMileage.lastOrNull()
                                    if (first != null && last != null) {
                                        Text("当月最初の走行距離: ${formatDouble(first.mileage ?: 0.0)} km (${first.date})", style = MaterialTheme.typography.bodySmall)
                                        Text("当月最終の走行距離: ${formatDouble(last.mileage ?: 0.0)} km (${last.date})", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("走行距離の増加: ${formatDouble(stats.totalMileage)} km", style = MaterialTheme.typography.bodySmall)
                                    Text("当月の給油:", style = MaterialTheme.typography.bodySmall)
                                    stats.fuelRecords.forEach { record ->
                                        Text(
                                            text = "${record.date}: ${formatDouble(record.fuel)} L${if (record.mileage == null) " (走行距離なし)" else ""}${if (record.isEstimated) " (推定)" else ""}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text("総給油量: ${formatDouble(stats.totalFuel)} L", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${formatDouble(stats.totalMileage)} km × ${formatDouble(stats.totalFuel)} L = ${formatDouble(stats.averageEfficiency)} km/L",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (selectedMonth.isBlank()) "月を選択してください。" else "選択した月は走行距離の記録が不足しています。少なくとも2件の走行距離を登録してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (stats != null && stats.records.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${selectedMonth} の記録", style = MaterialTheme.typography.titleMedium)
                    stats.records.asReversed().forEach { record ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(record.date, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "走行距離: ${record.mileage?.let { formatDouble(it) } ?: "未記録"} km | 給油量: ${formatDouble(record.fuel)} L",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (record.isEstimated) {
                                    Text("推定", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                                }
                                record.fuelEfficiency?.let {
                                    Text(
                                        text = "燃費: ${formatDouble(it)} km/L",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDouble(value: Double): String {
    return String.format(Locale.JAPAN, "%.2f", value)
}

private fun readTextFromUri(context: Context, uri: android.net.Uri): String {
    return context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
}

private fun writeTextToUri(context: Context, uri: android.net.Uri, text: String) {
    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
}


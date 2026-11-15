package com.yuu.supercubfuellog.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuu.supercubfuellog.MainViewModel

/** User-driven sharing and paste. Returning from an external app never applies or saves data. */
@Composable
fun AnalysisPasteSection(viewModel: MainViewModel) {
    val context = LocalContext.current
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    val ready by viewModel.isDataReady.collectAsStateWithLifecycle()

    val enabled = !loading && ready && !viewModel.photoBusy && viewModel.photoState.pending == null
    val awaitingPaste = viewModel.analysisFlow.awaitingPaste
    val paste: () -> Unit = {
        viewModel.performClickFeedback(context)
        try {
            // Read only in response to this explicit user action; never on resume.
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
            if (text.isNullOrBlank()) {
                viewModel.cancelAnalysisPaste()
                viewModel.postMessage("クリップボードにJSONテキストがありません。解析結果をコピーしてください。")
            } else viewModel.prepareAnalysisPaste(text)
        } catch (_: SecurityException) {
            viewModel.cancelAnalysisPaste()
            viewModel.postMessage("クリップボードを読み取れませんでした。もう一度お試しください。")
        }
    }
    val share: () -> Unit = {
        viewModel.performClickFeedback(context)
        val plan = viewModel.prepareAnalysisShare()
        if (plan != null) {
            try {
                context.startActivity(AnalysisShareIntent.chooser(context, plan, viewModel::photoUri))
                // A Sharesheet launch is not evidence of analysis or copying. Cancellation can be ambiguous.
                viewModel.analysisShareOpened(plan)
            } catch (_: ActivityNotFoundException) {
                viewModel.postMessage("共有先を開けませんでした。ChatGPTのインストール・更新を確認して再試行してください。")
            } catch (_: Exception) {
                viewModel.postMessage("画像を共有できませんでした。画像と入力は保持しています。もう一度お試しください。")
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = if (awaitingPaste) paste else share
        ) { Text(if (awaitingPaste) "貼り付け" else "ChatGPTで解析") }
        if (awaitingPaste) {
            TextButton(enabled = enabled, onClick = share) { Text("もう一度解析する") }
        } else {
            TextButton(enabled = enabled, onClick = paste) { Text("JSONを直接貼り付け") }
        }
        TextButton(enabled = enabled, onClick = {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("給油記録の解析指示", viewModel.analysisInstructions()))
                viewModel.postMessage("解析指示をコピーしました。ChatGPT側で貼り付けて送信してください。")
            } catch (_: Exception) {
                viewModel.postMessage("解析指示をコピーできませんでした。もう一度お試しください。")
            }
        }) { Text("解析指示をコピー") }
    }

    val preview = viewModel.analysisPreview
    if (preview != null) {
        val changes = buildList {
            if (preview.before.mileage != preview.after.mileage) {
                add("走行距離: ${preview.before.mileage.ifBlank { "未入力" }} → ${preview.after.mileage} km")
            }
            if (preview.before.fuel != preview.after.fuel) {
                add("給油量: ${preview.before.fuel.ifBlank { "未入力" }} → ${preview.after.fuel} L")
            }
        }.joinToString("\n")
        ConfirmDialog(
            title = "解析結果を入力欄へ反映",
            message = "$changes\n\n日付は変更しません。反映後も修正できます。保存は保存ボタンで行ってください。",
            confirmLabel = "反映する",
            confirmEnabled = !loading && ready,
            onConfirm = viewModel::confirmAnalysisPaste,
            onDismiss = viewModel::cancelAnalysisPaste
        )
    }
}

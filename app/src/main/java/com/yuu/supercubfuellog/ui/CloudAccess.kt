package com.yuu.supercubfuellog.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.yuu.supercubfuellog.MainViewModel
import com.yuu.supercubfuellog.R
import com.yuu.supercubfuellog.data.ApprovalGate

@Composable
private fun GoogleLoginButton(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    var pending by rememberSaveable { mutableStateOf(false) }
    val client = remember(context) {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail().build()
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending = false
        if (result.data == null && result.resultCode == Activity.RESULT_CANCELED) {
            viewModel.postMessage("Googleログインをキャンセルしました。")
        } else {
            viewModel.handleGoogleSignInResult(result.data)
        }
    }
    WelcomeLoginButton(
        enabled = !pending && !isSigningIn,
        inProgress = pending || isSigningIn,
        onClick = {
            try {
                pending = true
                launcher.launch(client.signInIntent)
            } catch (e: Exception) {
                pending = false
                viewModel.postMessage("Googleログイン画面を開けませんでした。Google Play開発者サービスを確認してください。")
            }
        }
    )
}

@Composable
fun LoginScreen(viewModel: MainViewModel) {
    val approvalMessage by viewModel.approvalMessage.collectAsStateWithLifecycle()
    LoginWelcome(approvalMessage = approvalMessage) {
        GoogleLoginButton(viewModel)
    }
}

@Composable
fun ApprovalCheckScreen(viewModel: MainViewModel) {
    val status by viewModel.approvalStatus.collectAsStateWithLifecycle()
    val message by viewModel.approvalMessage.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val signingIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    ScreenContainer {
        Text("利用承認の確認", style = MaterialTheme.typography.headlineSmall)
        Text(user?.email ?: user?.displayName ?: "Google認証済み")
        Text(if (status == ApprovalGate.Status.CHECKING) "管理者による承認状態を確認しています…" else message ?: "承認状態を確認できませんでした。")
        Text("確認が完了するまで記録の表示・操作はできません。")
        OutlinedButton(
            enabled = status != ApprovalGate.Status.CHECKING,
            onClick = viewModel::verifyApproval
        ) { Text("承認確認を再試行") }
        OutlinedButton(enabled = !signingIn, onClick = viewModel::signOut) { Text("ログアウト") }
    }
}

@Composable
fun CloudLoadingScreen(viewModel: MainViewModel) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    ScreenContainer {
        Text("クラウド記録を読み込みます", style = MaterialTheme.typography.headlineSmall)
        Text(user?.email ?: user?.displayName ?: "ログイン済み")
        CloudStatus(viewModel)
        if (!loading) {
            OutlinedButton(onClick = viewModel::refresh) { Text("再試行") }
        }
        OutlinedButton(enabled = !loading, onClick = viewModel::signOut) { Text("ログアウト") }
    }
}

@Composable
fun CloudStatus(viewModel: MainViewModel) {
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    val ready by viewModel.isDataReady.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ログイン本人のクラウド記録", style = MaterialTheme.typography.titleSmall)
        if (loading) Text("通信中…")
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (!loading && !ready) Text("読み込みが完了していません。通信状態を確認して再試行してください。")
    }
}

@Composable
fun CsvExportButton(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    val ready by viewModel.isDataReady.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        viewModel.finishExport(it)
    }
    OutlinedButton(
        modifier = modifier,
        enabled = !loading && ready,
        onClick = {
            if (viewModel.prepareExport(legacy = false)) {
                try {
                    launcher.launch("fuel_records.csv")
                } catch (e: Exception) {
                    viewModel.finishExport(null)
                    viewModel.postMessage("CSVの保存先を開けませんでした。再試行してください。")
                }
            }
        }
    ) { Text("記録をCSVに書き出す") }
}

@Composable
fun ImportConfirmation(viewModel: MainViewModel) {
    val preview = viewModel.importPreview ?: return
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = viewModel::cancelImport,
        title = { Text("CSVの取込確認") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("有効な記録: ${preview.plan.validCount}件")
                Text("追加予定: ${preview.plan.additions.size}件")
                Text("重複スキップ: ${preview.plan.duplicateCount}件")
                Text("不正な行のスキップ: ${preview.invalidCount}件")
                Text("日付・走行距離・給油量が同じ記録は重複として扱います。既存のクラウド記録は上書き・削除しません。再試行時は最新のクラウド記録で再判定します。")
                if (loading) Text("取込処理中…")
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && preview.plan.additions.isNotEmpty(),
                onClick = viewModel::confirmImport
            ) { Text("追加分を取り込む") }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = viewModel::cancelImport) {
                Text(if (preview.plan.additions.isEmpty()) "閉じる" else "キャンセル")
            }
        }
    )
}

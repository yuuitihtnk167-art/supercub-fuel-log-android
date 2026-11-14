package com.yuu.supercubfuellog

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.yuu.supercubfuellog.data.*
import com.yuu.supercubfuellog.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class CsvImportPreview(
    val owner: ApprovalGate.Lease,
    val rows: List<CsvRecord>,
    val plan: CsvImportPlan,
    val invalidCount: Int
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    // Keep the original database and schema. It is opened only for a read-only CSV rescue.
    private val database = Room.databaseBuilder(application, AppDatabase::class.java, "fuel_log.db").build()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val repository = RecordRepository(firestore, auth)
    private val approvalRepository = ApprovalRepository(firestore)
    private val approval = ApprovalGate(auth.currentUser?.uid)
    private var approvalListener: ListenerRegistration? = null
    private var approvalTimeout: Job? = null
    private var approvalCheck: ApprovalGate.Check? = null
    private val _approvalStatus = MutableStateFlow(approval.status)
    val approvalStatus = _approvalStatus.asStateFlow()
    private val _approvalMessage = MutableStateFlow<String?>(null)
    val approvalMessage = _approvalMessage.asStateFlow()
    private var dataJob: Job? = null

    private val _user = MutableStateFlow(auth.currentUser)
    val user = _user.asStateFlow()
    private val _records = MutableStateFlow<List<FuelRecord>>(emptyList())
    val records = _records.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isDataReady = MutableStateFlow(false)
    val isDataReady = _isDataReady.asStateFlow()
    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn = _isSigningIn.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    private val _hapticEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_ENABLED, true))
    val hapticEnabled = _hapticEnabled.asStateFlow()
    private val _darkThemeEnabled = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME_ENABLED, false))
    val darkThemeEnabled = _darkThemeEnabled.asStateFlow()
    private val _legacyCount = MutableStateFlow<Int?>(null)
    val legacyCount = _legacyCount.asStateFlow()
    private val _legacyError = MutableStateFlow<String?>(null)
    val legacyError = _legacyError.asStateFlow()
    private var legacyCsv: String? = null

    var draft by mutableStateOf(RecordDraft())
        private set
    var importPreview by mutableStateOf<CsvImportPreview?>(null)
        private set
    var pendingImportText by mutableStateOf<String?>(null)
        private set

    private data class ExportRequest(val csv: String, val owner: ApprovalGate.Lease?)
    private var exportRequest: ExportRequest? = null
    private var importSelectionOwner: ApprovalGate.Lease? = null
    private val authListener = FirebaseAuth.AuthStateListener {
        val changed = _user.value?.uid != it.currentUser?.uid
        _user.value = it.currentUser
        if (changed) {
            stopApprovalWatch()
            approval.selectAccount(it.currentUser?.uid)
            _approvalStatus.value = approval.status
            clearAccountData()
            if (it.currentUser != null) {
                _approvalMessage.value = null
                verifyApproval()
            }
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        if (auth.currentUser != null) verifyApproval()
        loadLegacyBackup()
    }

    private fun clearAccountData() {
        dataJob?.cancel()
        _isLoading.value = false
        _records.value = emptyList()
        _isDataReady.value = false
        _error.value = null
        draft = RecordDraft()
        importPreview = null
        pendingImportText = null
        exportRequest = null
        importSelectionOwner = null
    }

    private fun isCurrent(owner: ApprovalGate.Lease): Boolean =
        approval.accepts(owner) && auth.currentUser?.uid == owner.uid

    private fun stopApprovalWatch() {
        approvalListener?.remove()
        approvalListener = null
        approvalTimeout?.cancel()
        approvalTimeout = null
        approvalCheck = null
    }

    private fun blockCloudData() {
        dataJob?.cancel()
        _isLoading.value = false
        _records.value = emptyList()
        _isDataReady.value = false
        // Keep this account's draft and selected CSV text for a communication retry.
        importPreview = null
        exportRequest = null
        importSelectionOwner = null
    }

    fun verifyApproval() {
        if (_user.value == null) return
        stopApprovalWatch()
        val check = approval.beginCheck() ?: return
        approvalCheck = check
        blockCloudData()
        _approvalMessage.value = null
        _approvalStatus.value = approval.status
        try {
            approvalListener = approvalRepository.watch(check.uid,
                snapshot = { exists, enabled, fromCache, pendingWrites ->
                    if (approval.snapshot(check, exists, enabled, fromCache, pendingWrites)) publishApproval()
                },
                failure = {
                    if (approval.communicationFailure(check)) publishApproval()
                }
            )
            approvalTimeout = viewModelScope.launch {
                delay(15_000)
                if (approval.isCurrent(check) && approval.status == ApprovalGate.Status.CHECKING) {
                    approval.communicationFailure(check)
                    publishApproval()
                }
            }
        } catch (e: Exception) {
            approval.communicationFailure(check)
            publishApproval()
        }
    }

    private fun publishApproval() {
        val previous = _approvalStatus.value
        val next = approval.status
        _approvalStatus.value = next
        if (next != ApprovalGate.Status.CHECKING) approvalTimeout?.cancel()
        when (next) {
            ApprovalGate.Status.APPROVED -> {
                _approvalMessage.value = null
                if (previous != next) refresh()
            }
            ApprovalGate.Status.DENIED -> {
                forceSignOut("利用には管理者の承認が必要です")
            }
            ApprovalGate.Status.ERROR -> {
                blockCloudData()
                _approvalMessage.value = "承認状態を確認できませんでした。未承認とは判定していません。通信状態を確認して再試行してください。"
            }
            else -> Unit
        }
    }

    private fun approvalCommunicationFailure(owner: ApprovalGate.Lease) {
        if (!isCurrent(owner)) return
        approvalCheck?.let {
            if (approval.communicationFailure(it)) publishApproval()
        }
    }

    private fun approvalDenied(owner: ApprovalGate.Lease) {
        if (!isCurrent(owner)) return
        approvalCheck?.let {
            if (approval.snapshot(it, false, false, false)) publishApproval()
        }
    }

    fun onForeground() {
        // File pickers return to this Activity too. Their pending requests are checked
        // again by runCloud; don't invalidate them merely because the picker closed.
        if (_user.value != null && !_isLoading.value && !_isSigningIn.value &&
            exportRequest == null && importSelectionOwner == null &&
            approval.status != ApprovalGate.Status.CHECKING) verifyApproval()
    }

    private fun runCloud(
        failure: String,
        operation: suspend (ApprovalGate.Lease) -> Unit
    ) {
        if (_isLoading.value) return
        val owner = approval.lease()
        if (owner == null || !isCurrent(owner)) {
            postMessage("Googleログインと管理者の承認確認が必要です。")
            return
        }
        _isLoading.value = true
        _error.value = null
        dataJob = viewModelScope.launch {
            var verifying = true
            try {
                withTimeout(30_000) {
                    try {
                        approvalRepository.requireEnabled(owner.uid)
                    } catch (e: ApprovalRequiredException) {
                        approvalDenied(owner)
                        return@withTimeout
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        approvalCommunicationFailure(owner)
                        return@withTimeout
                    }
                    verifying = false
                    if (isCurrent(owner)) operation(owner)
                }
            } catch (e: TimeoutCancellationException) {
                if (verifying) approvalCommunicationFailure(owner)
                else if (isCurrent(owner)) fail("$failure 通信がタイムアウトしました。入力・取込内容は保持しています。再読み込みで結果を確認し、再試行してください。")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrent(owner)) {
                    if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        postMessage("データへのアクセスが拒否されました。承認状態を確認し直します。")
                        verifyApproval()
                    } else {
                        fail("$failure 入力・取込内容は保持しています。(${e.localizedMessage ?: e.javaClass.simpleName})")
                    }
                }
            } finally {
                if (approval.accepts(owner)) _isLoading.value = false
            }
        }
    }

    private fun fail(message: String) {
        _error.value = message
        postMessage(message)
    }

    fun postMessage(message: String) {
        viewModelScope.launch { _messages.emit(message) }
    }

    fun refresh() {
        runCloud("読み込みに失敗しました。") { owner ->
            val loaded = repository.load(owner.uid)
            if (isCurrent(owner)) {
                _records.value = FuelCalculator.recalculate(loaded)
                _isDataReady.value = true
            }
        }
    }

    fun updateDraft(date: String = draft.date, mileage: String = draft.mileage, fuel: String = draft.fuel) {
        if (!_isLoading.value && approval.lease() != null) draft = draft.copy(date = date, mileage = mileage, fuel = fuel)
    }

    fun startEditing(record: FuelRecord) {
        if (_isLoading.value || _records.value.none { it == record }) return
        draft = RecordDraft(record.id, record.date, record.mileage?.toString() ?: "", record.fuel.toString(), true)
    }

    fun cancelEditing() {
        if (!_isLoading.value) draft = RecordDraft()
    }

    fun saveRecord(record: FuelRecord) {
        if (!_isDataReady.value || record.id != draft.id) return
        runCloud("保存に失敗しました。") { owner ->
            repository.save(owner.uid, record)
            if (isCurrent(owner)) {
                _records.value = FuelCalculator.recalculate(_records.value.filterNot { it.id == record.id } + record)
                draft = RecordDraft()
                postMessage("保存しました。")
            }
        }
    }

    fun deleteRecord(id: String) {
        runCloud("削除に失敗しました。") { owner ->
            repository.delete(owner.uid, id)
            if (isCurrent(owner)) {
                _records.value = FuelCalculator.recalculate(_records.value.filterNot { it.id == id })
                postMessage("削除しました。")
            }
        }
    }

    fun deleteAll() {
        runCloud("全件削除の完了を確認できませんでした。") { owner ->
            repository.deleteAll(owner.uid)
            if (isCurrent(owner)) {
                _records.value = emptyList()
                postMessage("全件削除しました。")
            }
        }
    }

    fun beginImportSelection(): Boolean {
        val owner = approval.lease() ?: return false
        if (_isLoading.value || !isCurrent(owner)) return false
        importSelectionOwner = owner
        return true
    }

    fun finishImportSelection(uri: Uri?) {
        val owner = importSelectionOwner
        if (uri == null || owner == null || !isCurrent(owner)) {
            importSelectionOwner = null
            return
        }
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("CSVファイルを開けませんでした。")
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                if (isCurrent(owner)) prepareImport(text)
            } catch (e: Exception) {
                if (isCurrent(owner)) fail("CSVを読み込めませんでした。ファイルを選び直して再試行してください。")
            } finally {
                if (importSelectionOwner == owner) importSelectionOwner = null
            }
        }
    }

    fun prepareImport(text: String) {
        if (_isLoading.value || approval.lease() == null) return
        pendingImportText = text
        importPreview = null
        runCloud("取込内容の確認に失敗しました。") { owner ->
            val parsed = withContext(Dispatchers.Default) { CsvUtils.parseDetailed(text) }
            check(parsed.records.isNotEmpty()) { "有効なCSV記録がありません。不正な行: ${parsed.invalidCount}件" }
            val existing = repository.load(owner.uid)
            val plan = withContext(Dispatchers.Default) { CsvImportPlanner.plan(existing, parsed.records) }
            if (isCurrent(owner)) importPreview = CsvImportPreview(owner, parsed.records, plan, parsed.invalidCount)
        }
    }

    fun cancelImport() {
        if (_isLoading.value) return
        importPreview = null
        pendingImportText = null
    }

    fun confirmImport() {
        val preview = importPreview ?: return
        if (!isCurrent(preview.owner)) return
        runCloud("CSV取込の完了を確認できませんでした。一部が反映済みでも同じCSVを再試行できます。") { owner ->
            val existing = repository.load(owner.uid)
            val plan = withContext(Dispatchers.Default) { CsvImportPlanner.plan(existing, preview.rows) }
            repository.importNew(owner.uid, plan.additions.map { it.copy(lastUpdated = System.currentTimeMillis()) })
            val loaded = repository.load(owner.uid)
            if (isCurrent(owner)) {
                _records.value = FuelCalculator.recalculate(loaded)
                importPreview = null
                pendingImportText = null
                postMessage("取込完了: 追加対象${plan.additions.size}件、重複スキップ${plan.duplicateCount}件。")
            }
        }
    }

    fun loadLegacyBackup() {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val rows = database.recordDao().getAll().map { it.toDomain() }
                    rows.size to CsvUtils.generateCsv(rows)
                }
                _legacyCount.value = result.first
                legacyCsv = result.second
                _legacyError.value = null
            } catch (e: Exception) {
                _legacyError.value = "端末内の旧記録を読み込めませんでした。再試行してください。"
            }
        }
    }

    fun prepareExport(legacy: Boolean): Boolean {
        if (legacy) {
            val csv = legacyCsv ?: return false
            exportRequest = ExportRequest(csv, null)
        } else {
            val owner = approval.lease() ?: return false
            if (!isCurrent(owner) || !_isDataReady.value || _isLoading.value) return false
            exportRequest = ExportRequest(CsvUtils.generateCsv(_records.value), owner)
        }
        return true
    }

    fun finishExport(uri: Uri?) {
        val request = exportRequest
        exportRequest = null
        if (uri == null) return
        if (request == null || (request.owner != null && !isCurrent(request.owner))) {
            postMessage("ログイン状態が変わったためCSV出力を中止しました。")
            return
        }
        if (request.owner != null) {
            runCloud("CSV出力に失敗しました。") { owner ->
                check(owner == request.owner && isCurrent(owner)) { "承認状態が変わりました。" }
                writeExport(uri, request.csv)
                if (isCurrent(owner)) postMessage("CSVをエクスポートしました。")
            }
        } else {
            viewModelScope.launch {
                try {
                    writeExport(uri, request.csv)
                    postMessage("旧ローカル記録をCSVへ退避しました。")
                } catch (e: Exception) {
                    postMessage("CSV出力に失敗しました。元の記録は保持しています。再試行してください。")
                }
            }
        }
    }

    private suspend fun writeExport(uri: Uri, csv: String) {
        withContext(Dispatchers.IO) {
            val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
                ?: error("保存先を開けませんでした。")
            stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                for (chunk in csv.chunked(4096)) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    writer.write(chunk)
                }
            }
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        _hapticEnabled.value = enabled
        prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
    }

    fun setDarkThemeEnabled(enabled: Boolean) {
        _darkThemeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_DARK_THEME_ENABLED, enabled).apply()
    }

    fun performClickFeedback(context: Context) {
        if (!_hapticEnabled.value) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun handleGoogleSignInResult(data: Intent?) {
        if (_isSigningIn.value) return
        _isSigningIn.value = true
        viewModelScope.launch {
            try {
                check(data != null) { "Googleログイン結果を取得できませんでした。再試行してください。" }
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
                val token = account.idToken
                check(!token.isNullOrBlank()) { "default_web_client_id と Firebase のOAuth設定を確認してください。" }
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
            } catch (e: ApiException) {
                postMessage(when (e.statusCode) {
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Googleログインをキャンセルしました。"
                    CommonStatusCodes.NETWORK_ERROR -> "通信エラーでGoogleログインに失敗しました。"
                    CommonStatusCodes.DEVELOPER_ERROR -> "Googleログイン設定エラーです。SHA-1とFirebaseの設定を確認してください。"
                    else -> "Googleログインに失敗しました。(status=${e.statusCode})"
                })
            } catch (e: Exception) {
                postMessage("Googleログインに失敗しました。(${e.localizedMessage})")
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    fun signOut() {
        if (_isLoading.value || _isSigningIn.value) {
            postMessage("処理が完了してからログアウトしてください。")
            return
        }
        forceSignOut(null)
    }

    private fun forceSignOut(message: String?) {
        stopApprovalWatch()
        approval.selectAccount(null)
        _approvalStatus.value = approval.status
        clearAccountData()
        _approvalMessage.value = message
        _user.value = null
        auth.signOut()
        if (message != null) postMessage(message)
        _isSigningIn.value = true
        viewModelScope.launch {
            try {
                withTimeout(10_000) {
                    GoogleSignIn.getClient(getApplication<Application>(), GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .signOut().await()
                }
                if (message == null) postMessage("ログアウトしました。")
            } catch (e: Exception) {
                if (message == null) postMessage("Firebaseからログアウトしました。次回ログイン時にアカウントを確認してください。")
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    override fun onCleared() {
        stopApprovalWatch()
        auth.removeAuthStateListener(authListener)
        database.close()
        super.onCleared()
    }

    companion object {
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        private const val KEY_DARK_THEME_ENABLED = "dark_theme_enabled"
    }
}

package com.yuu.supercubfuellog

import android.app.Application
import android.content.Context
import android.content.Intent
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
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.yuu.supercubfuellog.data.AppDatabase
import com.yuu.supercubfuellog.data.DataSource
import com.yuu.supercubfuellog.data.FuelRecord
import com.yuu.supercubfuellog.data.RecordRepository
import com.yuu.supercubfuellog.util.FuelCalculator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val database: AppDatabase = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "fuel_log.db"
    ).build()

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val repository = RecordRepository(database.recordDao(), firestore, auth)

    private val _dataSource = MutableStateFlow(DataSource.LOCAL)
    val dataSource = _dataSource.asStateFlow()

    private val _records = MutableStateFlow<List<FuelRecord>>(emptyList())
    val records = _records.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _user = MutableStateFlow(auth.currentUser)
    val user = _user.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private val _hapticEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_ENABLED, true))
    val hapticEnabled = _hapticEnabled.asStateFlow()

    private val _darkThemeEnabled = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME_ENABLED, false))
    val darkThemeEnabled = _darkThemeEnabled.asStateFlow()

    var editingRecord: FuelRecord? by mutableStateOf(null)
        private set

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _user.value = firebaseAuth.currentUser
            if (_dataSource.value == DataSource.CLOUD) {
                refresh()
            }
        }
        refresh()
    }

    fun postMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
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
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.let {
            if (it.hasVibrator()) {
                it.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    fun setDataSource(source: DataSource) {
        _dataSource.value = source
        refresh()
    }

    fun startEditing(record: FuelRecord) {
        editingRecord = record
    }

    fun cancelEditing() {
        editingRecord = null
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val source = _dataSource.value
            val loaded = if (source == DataSource.CLOUD && auth.currentUser == null) {
                _messages.emit("クラウドを使うにはGoogleログインが必要です。")
                emptyList()
            } else {
                repository.load(source)
            }
            _records.value = FuelCalculator.recalculate(loaded)
            _isLoading.value = false
        }
    }

    fun saveRecord(record: FuelRecord, target: DataSource) {
        viewModelScope.launch {
            if (target == DataSource.CLOUD && auth.currentUser == null) {
                _messages.emit("クラウド保存にはGoogleログインが必要です。")
                return@launch
            }

            val current = repository.load(target).toMutableList()
            val existingIndex = current.indexOfFirst { it.id == record.id }
            if (existingIndex >= 0) {
                current[existingIndex] = record
            } else {
                current.add(record)
            }

            val recalculated = FuelCalculator.recalculate(current)
            repository.replaceAll(target, recalculated)

            if (_dataSource.value == target) {
                _records.value = recalculated
            }

            editingRecord = null
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            val source = _dataSource.value
            if (source == DataSource.CLOUD && auth.currentUser == null) {
                _messages.emit("クラウドを使うにはGoogleログインが必要です。")
                return@launch
            }
            val current = repository.load(source).filterNot { it.id == id }
            val recalculated = FuelCalculator.recalculate(current)
            repository.replaceAll(source, recalculated)
            _records.value = recalculated
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            val source = _dataSource.value
            if (source == DataSource.CLOUD && auth.currentUser == null) {
                _messages.emit("クラウドを使うにはGoogleログインが必要です。")
                return@launch
            }
            repository.deleteAll(source)
            _records.value = emptyList()
        }
    }

    fun importRecords(records: List<FuelRecord>, target: DataSource) {
        viewModelScope.launch {
            if (target == DataSource.CLOUD && auth.currentUser == null) {
                _messages.emit("クラウド保存にはGoogleログインが必要です。")
                return@launch
            }

            val current = repository.load(target).toMutableList()
            current.addAll(records)
            val recalculated = FuelCalculator.recalculate(current)
            repository.replaceAll(target, recalculated)

            if (_dataSource.value == target) {
                _records.value = recalculated
            }
        }
    }

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                if (data == null) {
                    _messages.emit("Googleログイン結果を取得できませんでした。もう一度お試しください。")
                    return@launch
                }

                val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    _messages.emit("Googleログイン設定が不正です。web_client_id と Firebase の OAuth 設定を確認してください。")
                    return@launch
                }

                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
            } catch (e: ApiException) {
                _messages.emit(buildGoogleSignInErrorMessage(e))
            } catch (e: Exception) {
                val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                _messages.emit("Googleログインに失敗しました。($detail)")
            }
        }
    }

    private fun buildGoogleSignInErrorMessage(e: ApiException): String {
        return when (e.statusCode) {
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Googleログインをキャンセルしました。"
            GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Googleログインに失敗しました。しばらくしてから再試行してください。"
            CommonStatusCodes.NETWORK_ERROR -> "ネットワークエラーでGoogleログインに失敗しました。通信状態を確認してください。"
            CommonStatusCodes.DEVELOPER_ERROR -> "Googleログイン設定エラーです。FirebaseでGoogleログインを有効化し、SHA-1登録後に google-services.json を再取得してください。"
            else -> "Googleログインに失敗しました。(status=${e.statusCode})"
        }
    }

    fun signOut(client: GoogleSignInClient) {
        viewModelScope.launch {
            client.signOut()
            auth.signOut()
            _messages.emit("ログアウトしました。")
            if (_dataSource.value == DataSource.CLOUD) {
                _records.value = emptyList()
            }
        }
    }

    companion object {
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        private const val KEY_DARK_THEME_ENABLED = "dark_theme_enabled"
    }
}

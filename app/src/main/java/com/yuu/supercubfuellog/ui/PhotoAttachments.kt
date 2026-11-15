package com.yuu.supercubfuellog.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuu.supercubfuellog.MainViewModel
import com.yuu.supercubfuellog.data.PhotoField
import com.yuu.supercubfuellog.data.PhotoFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias AcquirePhoto = (PhotoField, String?, Boolean) -> Unit

/** Register outside the account/approval navigation so rechecks cannot discard activity results. */
@Composable
fun rememberPhotoAcquisition(viewModel: MainViewModel): AcquirePhoto {
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        viewModel.finishPhotoCapture(it)
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        viewModel.finishPhotoSelection(listOfNotNull(it))
    }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(2)) {
        viewModel.finishPhotoSelection(it)
    }
    return { field, replace, takePicture ->
        val request = viewModel.beginPhotoAcquisition(field, replace, takePicture)
        if (request != null) {
            try {
                if (takePicture) camera.launch(viewModel.photoUri(request.cameraFileName!!))
                else {
                    val selection = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    if (field == PhotoField.RECEIPT && replace == null && viewModel.photoState.receipts.isEmpty())
                        multiple.launch(selection)
                    else single.launch(selection)
                }
            } catch (_: Exception) {
                viewModel.cancelPhotoAcquisition("画像選択画面またはカメラを開けませんでした。手入力は引き続き使えます。")
            }
        }
    }
}

@Composable
fun PhotoSourceButton(
    viewModel: MainViewModel,
    field: PhotoField,
    acquire: AcquirePhoto,
    replace: String? = null,
    icon: Boolean = true,
    buttonLabel: String = "差し替え・撮り直し"
) {
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    val ready by viewModel.isDataReady.collectAsStateWithLifecycle()
    val enabled = ready && !loading && !viewModel.photoBusy && viewModel.photoState.pending == null
    var expanded by remember { mutableStateOf(false) }
    val label = if (field == PhotoField.METER) "メーター画像" else "レシート画像"
    Box {
        if (icon) {
            IconButton(enabled = enabled, onClick = { expanded = true }) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "$label を選択・撮影")
            }
        } else {
            OutlinedButton(enabled = enabled, onClick = { expanded = true }) { Text(buttonLabel) }
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("画像を選択") }, onClick = {
                expanded = false
                acquire(field, replace, false)
            })
            DropdownMenuItem(text = { Text("カメラで撮影") }, onClick = {
                expanded = false
                acquire(field, replace, true)
            })
        }
    }
}

@Composable
fun PhotoAttachments(viewModel: MainViewModel, field: PhotoField, acquire: AcquirePhoto) {
    val context = LocalContext.current
    val store = remember(context) { PhotoFileStore(context.applicationContext) }
    val photos = viewModel.photoState.items(field)
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    if (photos.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (field == PhotoField.RECEIPT) {
                Text("同じ給油のレシートを2枚まで追加できます。別の日や同じレシートの重複にご注意ください。")
            }
            photos.forEachIndexed { index, photo ->
                val bitmap by produceState<Bitmap?>(null, photo.fileName) {
                    value = withContext(Dispatchers.IO) { runCatching { store.thumbnail(photo.fileName) }.getOrNull() }
                }
                Text(if (field == PhotoField.METER) "メーター" else "レシート ${index + 1}")
                val preview = bitmap
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = if (field == PhotoField.METER) "メーター写真" else "レシート写真 ${index + 1}",
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentScale = ContentScale.Fit
                    )
                } else Text("画像を表示できない場合は選び直してください。")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoSourceButton(viewModel, field, acquire, replace = photo.fileName, icon = false)
                    TextButton(
                        enabled = !loading && !viewModel.photoBusy && viewModel.photoState.pending == null,
                        onClick = { viewModel.removePhoto(field, photo.fileName) }
                    ) { Text("削除") }
                }
            }
            if (field == PhotoField.RECEIPT && photos.size < 2) {
                PhotoSourceButton(
                    viewModel = viewModel,
                    field = PhotoField.RECEIPT,
                    acquire = acquire,
                    replace = null,
                    icon = false,
                    buttonLabel = "レシートを追加（2枚目）"
                )
            }
        }
    }
    if (field == viewModel.photoState.pending?.field) {
        if (viewModel.photoBusy) Text("画像を読み込み中…")
        else TextButton(onClick = { viewModel.cancelPhotoAcquisition() }) { Text("画像の取得を中止") }
    }
}

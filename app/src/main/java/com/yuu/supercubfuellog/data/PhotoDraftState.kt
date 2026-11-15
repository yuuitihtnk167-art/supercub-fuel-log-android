package com.yuu.supercubfuellog.data

import java.io.Serializable
import java.util.UUID

enum class PhotoField(val limit: Int) { METER(1), RECEIPT(2) }

data class DraftPhoto(val fileName: String, val fingerprint: String) : Serializable

data class PhotoRequest(
    val token: String,
    val ownerUid: String,
    val recordId: String,
    val epoch: String,
    val field: PhotoField,
    val replaceFileName: String?,
    val cameraFileName: String?
) : Serializable

/** Only references and request metadata are saved; never put bitmap data in saved state. */
data class PhotoDraftState(
    val meter: List<DraftPhoto> = emptyList(),
    val receipts: List<DraftPhoto> = emptyList(),
    val pending: PhotoRequest? = null,
    val processing: Boolean = false,
    val epoch: String = UUID.randomUUID().toString()
) : Serializable {
    fun items(field: PhotoField): List<DraftPhoto> = if (field == PhotoField.METER) meter else receipts
    fun all(): List<DraftPhoto> = meter + receipts

    fun begin(uid: String, recordId: String, field: PhotoField, replace: String?, camera: String?): PhotoDraftState {
        require(pending == null) { "画像を取得中です。完了してから操作してください。" }
        val current = items(field)
        require(if (replace == null) current.size < field.limit else current.any { it.fileName == replace }) {
            if (replace == null) "画像は上限に達しています。個別の変更・削除を使ってください。"
            else "変更対象の画像がありません。"
        }
        return copy(pending = PhotoRequest(UUID.randomUUID().toString(), uid, recordId, epoch, field, replace, camera))
    }

    fun accepts(request: PhotoRequest, uid: String?, recordId: String): Boolean =
        pending == request && request.ownerUid == uid && request.recordId == recordId && request.epoch == epoch

    /** Validate the entire selection before returning a new state. Empty means cancellation. */
    fun complete(request: PhotoRequest, selected: List<DraftPhoto>, uid: String?, recordId: String): PhotoDraftState {
        require(accepts(request, uid, recordId)) { "画像の取得先が変わりました。" }
        if (selected.isEmpty()) return copy(pending = null, processing = false)
        val remaining = items(request.field).filterNot { it.fileName == request.replaceFileName }
        require(request.replaceFileName == null || selected.size == 1) { "差し替え画像は1枚選択してください。" }
        require(remaining.size + selected.size <= request.field.limit) { "レシートは最大2枚、メーターは1枚です。" }
        val others = all().filterNot { it.fileName == request.replaceFileName }
        val fingerprints = (others + selected).map { it.fingerprint }
        require(fingerprints.distinct().size == fingerprints.size) { "同じ画像が含まれています。重複しない画像を選んでください。" }
        val updated = if (request.replaceFileName == null) items(request.field) + selected
            else items(request.field).map { if (it.fileName == request.replaceFileName) selected.single() else it }
        return if (request.field == PhotoField.METER) copy(meter = updated, pending = null, processing = false)
        else copy(receipts = updated, pending = null, processing = false)
    }

    fun cancel(request: PhotoRequest): PhotoDraftState =
        if (pending == request) copy(pending = null, processing = false) else this

    /** An interrupted local copy cannot be resumed from a transient picker URI. Keep prior photos. */
    fun recoverInterruptedCopy(): PhotoDraftState =
        if (processing) copy(pending = null, processing = false) else this

    fun remove(field: PhotoField, fileName: String): PhotoDraftState {
        require(pending == null) { "画像を取得中です。" }
        return if (field == PhotoField.METER) copy(meter = meter.filterNot { it.fileName == fileName })
        else copy(receipts = receipts.filterNot { it.fileName == fileName })
    }
}

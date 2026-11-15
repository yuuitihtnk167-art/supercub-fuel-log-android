package com.yuu.supercubfuellog.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Private, non-backed-up cache. Only this dedicated directory is exposed by FileProvider. */
class PhotoFileStore(private val context: Context) {
    private val directory = File(context.cacheDir, "record_photos").apply { mkdirs() }

    fun createCameraFile(): String = File.createTempFile("capture_", ".jpg", directory).name
    fun file(name: String): File {
        require(name.matches(Regex("[A-Za-z0-9_-]+\\.jpg"))) { "画像ファイル名が正しくありません。" }
        return File(directory, name)
    }
    fun uri(name: String): Uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file(name))
    fun exists(name: String): Boolean = runCatching { file(name).let { it.isFile && it.length() > 0 } }.getOrDefault(false)

    /** Copy the selected bytes locally so later access never depends on the source provider's grant. */
    fun importImage(uri: Uri): DraftPhoto {
        val name = "${UUID.randomUUID()}.jpg"
        val output = file(name)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val stream = context.contentResolver.openInputStream(uri) ?: error("画像を開けませんでした。")
            stream.use { input ->
                output.outputStream().use { destination ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_BYTES) { "画像は32MB以下を選択してください。" }
                        digest.update(buffer, 0, read)
                        destination.write(buffer, 0, read)
                    }
                }
            }
            // Decode a bounded preview now: invalid or unsupported content must not replace an old photo.
            val preview = thumbnail(name) ?: error("画像を読み込めませんでした。別の画像を選んでください。")
            preview.recycle()
            return DraftPhoto(name, digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) })
        } catch (e: Exception) {
            output.delete()
            throw e
        }
    }

    fun thumbnail(name: String): Bitmap? {
        val source = file(name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 768) sample *= 2
        val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val orientation = runCatching {
            ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    fun delete(name: String) {
        runCatching {
            context.revokeUriPermission(uri(name), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            file(name).delete()
        }
    }

    /** Only called at ViewModel creation, before a new acquisition can start. */
    fun pruneExcept(keep: Set<String>) {
        directory.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { delete(it.name) }
    }

    companion object { private const val MAX_BYTES = 32L * 1024 * 1024 }
}

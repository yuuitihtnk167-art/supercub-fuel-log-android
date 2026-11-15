package com.yuu.supercubfuellog.data

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider

class RecordPhotoProvider : FileProvider() {
    // Picker files keep their original bytes, even when the private cache name ends in .jpg.
    // Report the actual format so PNG/WebP etc. are not mislabeled for the receiving app.
    override fun getType(uri: Uri): String? = runCatching {
        super.openFile(uri, "r")?.use { descriptor ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
            options.outMimeType
        }
    }.getOrNull() ?: super.getType(uri)
}

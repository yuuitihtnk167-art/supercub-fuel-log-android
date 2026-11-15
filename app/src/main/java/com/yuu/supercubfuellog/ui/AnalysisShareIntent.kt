package com.yuu.supercubfuellog.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yuu.supercubfuellog.util.AnalysisSharePlan

/** Standard Sharesheet only. No private ChatGPT components, URL schemes, or response callback. */
object AnalysisShareIntent {
    fun chooser(context: Context, plan: AnalysisSharePlan, uriFor: (String) -> Uri): Intent {
        val uris = ArrayList(plan.photoFiles.map(uriFor))
        require(uris.size in 1..3 && uris.all { it.scheme == "content" }) { "共有できる画像がありません。" }
        val streams = ClipData.newUri(context.contentResolver, "給油記録の画像", uris.first())
        uris.drop(1).forEach { streams.addItem(ClipData.Item(it)) }
        val send = Intent(if (plan.multiple) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_TEXT, plan.instructions)
            putExtra(Intent.EXTRA_TITLE, "給油記録を読み取り")
            if (plan.multiple) putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            else putExtra(Intent.EXTRA_STREAM, uris.single())
            clipData = streams
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "ChatGPTを選んでください").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

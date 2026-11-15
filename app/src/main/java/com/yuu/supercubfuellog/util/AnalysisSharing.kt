package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.PhotoDraftState
import java.io.Serializable

data class AnalysisSharePlan(
    val ownerUid: String,
    val recordId: String,
    val photoFiles: List<String>,
    val instructions: String
) {
    val multiple: Boolean get() = photoFiles.size > 1

    companion object {
        fun create(uid: String, recordId: String, photos: PhotoDraftState): AnalysisSharePlan {
            require(photos.pending == null && !photos.processing) { "画像の取得が完了してから解析してください。" }
            require(photos.meter.size <= 1 && photos.receipts.size <= 2) { "メーターは1枚、レシートは2枚までです。" }
            val files = photos.all().map { it.fileName }
            require(files.isNotEmpty()) { "メーターまたはレシートの画像を用意してください。" }
            require(files.distinct().size == files.size) { "同じ画像が含まれています。" }
            return AnalysisSharePlan(uid, recordId, files, AnalysisInstructions.create(photos.meter.size, photos.receipts.size))
        }
    }
}

/** Waiting means the share UI opened, not that ChatGPT received, sent, or analyzed anything. */
data class AnalysisFlowState(
    val ownerUid: String? = null,
    val recordId: String? = null,
    val photoFiles: List<String> = emptyList(),
    val awaitingPaste: Boolean = false
) : Serializable {
    fun forContext(uid: String?, id: String, files: List<String>): AnalysisFlowState =
        if (ownerUid == uid && recordId == id && photoFiles == files) this else AnalysisFlowState()

    fun shareOpened(plan: AnalysisSharePlan): AnalysisFlowState =
        AnalysisFlowState(plan.ownerUid, plan.recordId, plan.photoFiles.toList(), true)

    fun pasteConfirmed(): AnalysisFlowState = AnalysisFlowState()
}

object AnalysisInstructions {
    fun create(meterCount: Int, receiptCount: Int): String {
        require(meterCount in 0..1 && receiptCount in 0..2)
        return """
            添付画像から給油記録を読み取り、MarkdownのJSONコードブロック1つだけを返してください。開始行は```json、終了行は```とし、コードブロックの外には説明文を付けないでください。
            アプリで用意した画像はメーター${meterCount}枚、レシート${receiptCount}枚です。
            左右・並び順・割り当てだけで判断せず、写真の内容を確認して項目を識別してください。不足・取り違え・不一致がある項目はnullにしてください。
            mileage_km: メーターの総走行距離をkm単位で読み取ってください。速度計の針の値ではありません。
            機械式メーターの右端の色が異なる桁などを確認し、構造から判別できる場合は小数第1位として扱ってください。小数点が印刷されていなくても桁を省略しないでください。
            fuel_l: レシートの給油量をL単位で読み取ってください。金額や単価と混同しないでください。
            レシートが2枚なら、同じ給油のものとしてそれぞれの給油量を合算し、合計だけをfuel_lに返してください。
            2枚のうち一方でも読めない場合は、不完全な合計を出さずfuel_lをnullにしてください。
            同じレシートの重複や別の日の給油が疑われる場合も、二重計上・合算せずfuel_lをnullにしてください。
            読めない値、確信の持てない値、対応する写真がない項目はnullにし、推測で埋めないでください。
            数値は小数を保持し、引用符・単位・桁区切りは付けないでください。日付など他のキーは出力しないでください。
            キーは必ずmileage_kmとfuel_lの2つです。形式例（以下の数値を転記せず、写真から読み取ること）:
            ```json
            {"mileage_km":68982.6,"fuel_l":2.13}
            ```
        """.trimIndent()
    }
}

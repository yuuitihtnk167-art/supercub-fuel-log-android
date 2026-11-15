package com.yuu.supercubfuellog.util

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.yuu.supercubfuellog.data.RecordDraft
import java.io.IOException
import java.io.StringReader

/** A validated patch; null means leave the existing input unchanged. */
class AnalysisValues internal constructor(val mileage: String?, val fuel: String?) {
    fun applyTo(draft: RecordDraft): RecordDraft = draft.copy(
        mileage = mileage ?: draft.mileage,
        fuel = fuel ?: draft.fuel
    )
}

sealed interface AnalysisJsonResult {
    data class Valid(val values: AnalysisValues) : AnalysisJsonResult
    data class Invalid(val message: String) : AnalysisJsonResult
}

/** Strict JSON only, with an optional complete Markdown code fence. No Android dependencies. */
object AnalysisJsonParser {
    private val fence = Regex("\\A```(?:json)?[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n```\\z", RegexOption.IGNORE_CASE)
    const val MAX_TEXT_LENGTH = 16_384

    fun parse(text: String): AnalysisJsonResult {
        if (text.length > MAX_TEXT_LENGTH) return AnalysisJsonResult.Invalid("JSONが長すぎます。数値だけの解析結果をコピーしてください。")
        val trimmed = text.trim()
        val json = fence.matchEntire(trimmed)?.groupValues?.get(1) ?: trimmed
        return try {
            JsonReader(StringReader(json)).use { reader ->
                reader.setStrictness(Strictness.STRICT)
                require(reader.peek() == JsonToken.BEGIN_OBJECT) { "JSONはオブジェクト形式で指定してください。" }
                reader.beginObject()
                val seen = mutableSetOf<String>()
                var mileage: String? = null
                var fuel: String? = null
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(name == "mileage_km" || name == "fuel_l") { "対応していないキーです: $name" }
                    require(seen.add(name)) { "同じキーを複数指定できません: $name" }
                    val value = when (reader.peek()) {
                        JsonToken.NULL -> { reader.nextNull(); null }
                        JsonToken.NUMBER -> validateNumber(name, reader.nextString())
                        else -> throw IllegalArgumentException("$name は数値またはnullで指定してください。")
                    }
                    if (name == "mileage_km") mileage = value else fuel = value
                }
                reader.endObject()
                require(reader.peek() == JsonToken.END_DOCUMENT) { "JSONの後に余分な内容があります。" }
                AnalysisJsonResult.Valid(AnalysisValues(mileage, fuel))
            }
        } catch (e: IllegalArgumentException) {
            AnalysisJsonResult.Invalid(e.message ?: "数値が正しくありません。")
        } catch (e: IOException) {
            AnalysisJsonResult.Invalid("JSON形式が正しくありません。解析結果のJSON全体をコピーしてください。")
        } catch (e: IllegalStateException) {
            AnalysisJsonResult.Invalid("JSON形式が正しくありません。")
        }
    }

    private fun validateNumber(name: String, raw: String): String =
        RecordNumberValidator.validate(raw, isMileage = name == "mileage_km")
}

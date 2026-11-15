package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.CsvRecord
import com.yuu.supercubfuellog.data.FuelRecord
import java.time.LocalDate
import java.util.Locale

data class CsvParseResult(val records: List<CsvRecord>, val invalidCount: Int)

object CsvUtils {
    fun parseCsv(text: String): List<CsvRecord> = parseDetailed(text).records

    fun parseDetailed(text: String): CsvParseResult {
        val rows = text.lineSequence().filter { it.isNotBlank() }.map(::parseCsvLine).toList()
        var header = -1
        var dateIndex = -1
        var mileageIndex = -1
        var fuelIndex = -1
        for (i in rows.indices) {
            val names = rows[i].map { it.replace("\uFEFF", "").trim().lowercase(Locale.ROOT) }
            val date = names.indexOfFirst { it.contains("日付") || it == "date" }
            val fuel = names.indexOfFirst {
                it.contains("給油") || it.contains("燃料") || it == "fuel" || it.startsWith("fuel(")
            }
            if (date >= 0 && fuel >= 0) {
                header = i
                dateIndex = date
                fuelIndex = fuel
                mileageIndex = names.indexOfFirst { it.contains("走行") || it.startsWith("mileage") }
                break
            }
        }
        if (header < 0) return CsvParseResult(emptyList(), (rows.size - 1).coerceAtLeast(0))

        val records = mutableListOf<CsvRecord>()
        var invalid = 0
        for (values in rows.drop(header + 1)) {
            val date = normalizeDate(values.getOrNull(dateIndex)?.trim() ?: "")
            val fuel = parseNumber(values.getOrNull(fuelIndex) ?: "", isMileage = false)
            val mileageText = values.getOrNull(mileageIndex)?.trim() ?: ""
            val mileage = if (mileageText.isBlank()) null else parseNumber(mileageText, isMileage = true)
            if (date == null || fuel == null || !fuel.isFinite() || fuel <= 0 ||
                (mileageText.isNotBlank() && (mileage == null || !mileage.isFinite() || mileage < 0))) {
                invalid++
            } else {
                records.add(CsvRecord(date, mileage, fuel))
            }
        }
        return CsvParseResult(records, invalid)
    }

    private fun normalizeDate(value: String): String? = runCatching {
        val parts = value.replace("/", "-").split("-")
        require(parts.size == 3)
        LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()).toString()
    }.getOrNull()

    private fun parseNumber(value: String, isMileage: Boolean): Double? = runCatching {
        val normalized = value.trim().replace(",", "").replace(Regex("(?i)\\s*(km|l)$"), "").trim()
        RecordNumberValidator.validate(normalized, isMileage).toDouble()
    }.getOrNull()

    fun generateCsv(records: List<FuelRecord>): String {
        val headers = listOf("日付", "走行距離(km)", "給油量(L)", "燃費(km/L)")
        val rows = records.map { record ->
            listOf(
                record.date,
                record.mileage?.toString() ?: "",
                record.fuel.toString(),
                record.fuelEfficiency?.toString() ?: ""
            ).joinToString(",")
        }
        return (listOf(headers.joinToString(",")) + rows).joinToString("\n")
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        var i = 0
        while (i < line.length) {
            val char = line[i]
            if (char == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
                i++
                continue
            }

            if (char == ',' && !inQuotes) {
                result.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(char)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

}

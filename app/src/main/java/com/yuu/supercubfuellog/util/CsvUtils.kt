package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.CsvRecord
import com.yuu.supercubfuellog.data.FuelRecord
import java.util.Locale

object CsvUtils {
    fun parseCsv(text: String): List<CsvRecord> {
        val lines = text.split(Regex("\\r?\\n")).filter { it.trim().isNotEmpty() }
        if (lines.size < 2) return emptyList()

        val rows = lines.map { parseCsvLine(it) }
        var headerRowIndex = -1
        var dateIndex = -1
        var mileageIndex = -1
        var fuelIndex = -1

        for (i in rows.indices) {
            val headers = rows[i].map { it.replace("\uFEFF", "").trim() }
            val dateIdx = headers.indexOfFirst { it.contains("日付") || it.lowercase(Locale.JAPAN).contains("date") }
            val mileageIdx = headers.indexOfFirst { it.contains("走行") || it.lowercase(Locale.JAPAN).contains("mileage") }
            val fuelIdx = headers.indexOfFirst {
                it.contains("燃料") || it.contains("給油") || it.lowercase(Locale.JAPAN).contains("fuel")
            }

            if (dateIdx != -1 && fuelIdx != -1) {
                headerRowIndex = i
                dateIndex = dateIdx
                mileageIndex = mileageIdx
                fuelIndex = fuelIdx
                break
            }
        }

        if (headerRowIndex == -1 || dateIndex == -1 || fuelIndex == -1) return emptyList()

        val result = mutableListOf<CsvRecord>()
        for (i in headerRowIndex + 1 until rows.size) {
            val values = rows[i].map { it.trim() }
            if (values.size <= maxOf(dateIndex, fuelIndex)) continue

            var date = values[dateIndex]
            var fuel = values[fuelIndex]
            var mileage = if (mileageIndex != -1 && values.size > mileageIndex) values[mileageIndex] else ""

            if (date.contains("/")) {
                date = date.replace("/", "-")
            }

            fuel = cleanNumber(fuel)
            mileage = cleanNumber(mileage)

            if (fuel.isBlank() || fuel == "#DIV/0!" || fuel.lowercase(Locale.JAPAN).contains("div/0")) {
                continue
            }

            val fuelNum = fuel.toDoubleOrNull() ?: continue
            if (fuelNum <= 0) continue

            val mileageNum = mileage.toDoubleOrNull()

            if (date.isBlank()) continue

            result.add(
                CsvRecord(
                    date = date,
                    mileage = mileageNum,
                    fuel = fuelNum
                )
            )
        }

        return result
    }

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

    private fun cleanNumber(value: String): String {
        return value.replace(",", "").replace(Regex("[^\\d.-]"), "")
    }
}

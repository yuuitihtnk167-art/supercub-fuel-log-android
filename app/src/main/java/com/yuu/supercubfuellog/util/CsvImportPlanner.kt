package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.CsvRecord
import com.yuu.supercubfuellog.data.FuelRecord
import java.math.BigDecimal
import java.security.MessageDigest

data class CsvImportPlan(
    val validCount: Int,
    val duplicateCount: Int,
    val additions: List<FuelRecord>
)

object CsvImportPlanner {
    private fun number(value: Double): String =
        BigDecimal.valueOf(if (value == 0.0) 0.0 else value).stripTrailingZeros().toPlainString()

    private fun key(date: String, mileage: Double?, fuel: Double): String =
        "$date|${mileage?.let(::number) ?: "none"}|${number(fuel)}"

    fun plan(existing: List<FuelRecord>, rows: List<CsvRecord>): CsvImportPlan {
        val keys = existing.map { key(it.date, it.mileage, it.fuel) }.toMutableSet()
        val ids = existing.map { it.id }.toMutableSet()
        val additions = mutableListOf<FuelRecord>()
        rows.forEach { row ->
            require(row.fuel.isFinite() && row.fuel > 0)
            require(row.mileage == null || (row.mileage.isFinite() && row.mileage >= 0))
            val key = key(row.date, row.mileage, row.fuel)
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(key.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val id = "csv_$hash"
            // An imported record may have been edited since the previous import.
            if (key !in keys && id !in ids) {
                additions.add(FuelRecord(id, row.date, row.mileage, row.fuel))
            }
            keys.add(key)
            ids.add(id)
        }
        return CsvImportPlan(rows.size, rows.size - additions.size, additions)
    }
}

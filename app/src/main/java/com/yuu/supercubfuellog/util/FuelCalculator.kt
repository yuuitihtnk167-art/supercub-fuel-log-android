package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.FuelRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object FuelCalculator {
    private val formatter = DateTimeFormatter.ISO_DATE

    fun recalculate(records: List<FuelRecord>): List<FuelRecord> {
        val sorted = records.sortedBy { toEpochDaySafe(it.date) }
        val estimated = estimateFuel(sorted)
        val result = mutableListOf<FuelRecord>()

        for (i in estimated.indices) {
            val current = estimated[i]
            if (i == 0) {
                result.add(current.copy(fuelEfficiency = null))
                continue
            }

            if (current.mileage != null) {
                var lastMileageIndex = i - 1
                while (lastMileageIndex >= 0 && estimated[lastMileageIndex].mileage == null) {
                    lastMileageIndex--
                }

                if (lastMileageIndex >= 0 && estimated[lastMileageIndex].mileage != null) {
                    var totalFuel = current.fuel
                    for (j in lastMileageIndex + 1 until i) {
                        totalFuel += estimated[j].fuel
                    }

                    if (totalFuel > 0) {
                        val distance = current.mileage - estimated[lastMileageIndex].mileage!!
                        val efficiency = distance / totalFuel
                        result.add(current.copy(fuelEfficiency = efficiency))
                    } else {
                        result.add(current.copy(fuelEfficiency = null))
                    }
                } else {
                    result.add(current.copy(fuelEfficiency = null))
                }
            } else {
                result.add(current.copy(fuelEfficiency = null))
            }
        }

        return result
    }

    private fun estimateFuel(records: List<FuelRecord>): List<FuelRecord> {
        return records.mapIndexed { index, record ->
            if (record.isEstimated || record.fuel > 0) return@mapIndexed record

            if (record.mileage != null && index > 0) {
                val efficiencies = mutableListOf<Double>()

                var i = index - 1
                while (i >= 0 && efficiencies.size < 3) {
                    val e = records[i].fuelEfficiency
                    if (e != null && e > 0) efficiencies.add(e)
                    i--
                }

                i = index + 1
                while (i < records.size && efficiencies.size < 6) {
                    val e = records[i].fuelEfficiency
                    if (e != null && e > 0) efficiencies.add(e)
                    i++
                }

                if (efficiencies.isNotEmpty()) {
                    val avgEfficiency = efficiencies.sum() / efficiencies.size

                    var prevMileage: Double? = null
                    var j = index - 1
                    while (j >= 0) {
                        if (records[j].mileage != null) {
                            prevMileage = records[j].mileage
                            break
                        }
                        j--
                    }

                    if (prevMileage != null && record.mileage > prevMileage) {
                        val distance = record.mileage - prevMileage
                        val estimatedFuel = distance / avgEfficiency
                        return@mapIndexed record.copy(
                            fuel = estimatedFuel,
                            isEstimated = true
                        )
                    }
                }
            }

            record
        }
    }

    private fun toEpochDaySafe(date: String): Long {
        return runCatching { LocalDate.parse(date, formatter).toEpochDay() }.getOrDefault(Long.MIN_VALUE)
    }
}

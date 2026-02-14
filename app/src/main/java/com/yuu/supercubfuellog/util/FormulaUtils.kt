package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.FormulaInfo
import com.yuu.supercubfuellog.data.FuelRecord
import com.yuu.supercubfuellog.data.IntermediateFuel

object FormulaUtils {
    fun getFormulaInfo(records: List<FuelRecord>, record: FuelRecord): FormulaInfo? {
        val index = records.indexOfFirst { it.id == record.id }
        if (index == -1) return null
        val efficiency = record.fuelEfficiency ?: return null
        val currentMileage = record.mileage ?: return null
        if (record.fuel <= 0) return null

        var lastMileageIndex = index - 1
        while (lastMileageIndex >= 0 && records[lastMileageIndex].mileage == null) {
            lastMileageIndex--
        }
        if (lastMileageIndex < 0 || records[lastMileageIndex].mileage == null) return null

        val prevRecord = records[lastMileageIndex]
        val distance = currentMileage - prevRecord.mileage!!

        var totalFuel = record.fuel
        val intermediateFuels = mutableListOf<IntermediateFuel>()
        for (j in lastMileageIndex + 1 until index) {
            val fuelAmount = records[j].fuel
            if (fuelAmount > 0) {
                totalFuel += fuelAmount
                intermediateFuels.add(
                    IntermediateFuel(
                        date = records[j].date,
                        fuel = fuelAmount,
                        isEstimated = records[j].isEstimated
                    )
                )
            }
        }

        return FormulaInfo(
            prevDate = prevRecord.date,
            prevMileage = prevRecord.mileage!!,
            currentMileage = currentMileage,
            distance = distance,
            intermediateFuels = intermediateFuels,
            currentFuel = record.fuel,
            currentIsEstimated = record.isEstimated,
            totalFuel = totalFuel,
            efficiency = efficiency
        )
    }
}

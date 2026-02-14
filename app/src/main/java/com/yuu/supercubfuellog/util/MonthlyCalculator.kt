package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.FuelRecord

data class MonthlyStats(
    val records: List<FuelRecord>,
    val recordsWithMileage: List<FuelRecord>,
    val totalMileage: Double,
    val totalFuel: Double,
    val averageEfficiency: Double?,
    val previousMonthLastMileage: Double?,
    val previousMonthLastDate: String?,
    val isFirstRecordEver: Boolean,
    val fuelRecords: List<FuelRecord>,
    val hasEstimated: Boolean
)

object MonthlyCalculator {
    fun calculate(records: List<FuelRecord>, selectedMonth: String): MonthlyStats? {
        val monthly = records.filter { DateUtils.toYearMonth(it.date) == selectedMonth }
            .sortedBy { it.date }
        if (monthly.isEmpty()) return null

        val recordsWithMileage = monthly.filter { it.mileage != null }
        if (recordsWithMileage.size < 2) {
            return MonthlyStats(
                records = monthly,
                recordsWithMileage = recordsWithMileage,
                totalMileage = 0.0,
                totalFuel = 0.0,
                averageEfficiency = null,
                previousMonthLastMileage = null,
                previousMonthLastDate = null,
                isFirstRecordEver = false,
                fuelRecords = monthly,
                hasEstimated = monthly.any { it.isEstimated }
            )
        }

        val firstRecordInMonth = recordsWithMileage.first()
        val lastRecordInMonth = recordsWithMileage.last()

        val allSortedRecords = records.filter { it.mileage != null }.sortedBy { it.date }
        val firstRecordIndex = allSortedRecords.indexOfFirst { it.id == firstRecordInMonth.id }
        val isFirstRecordEver = firstRecordIndex == 0

        val previousMonthLastMileage = if (firstRecordIndex > 0) {
            allSortedRecords[firstRecordIndex - 1].mileage
        } else {
            firstRecordInMonth.mileage
        }
        val previousMonthLastDate = if (firstRecordIndex > 0) {
            allSortedRecords[firstRecordIndex - 1].date
        } else {
            null
        }

        val totalFuelInMonth = if (isFirstRecordEver) {
            monthly.drop(1).sumOf { it.fuel }
        } else {
            monthly.sumOf { it.fuel }
        }

        val mileageIncrease = (lastRecordInMonth.mileage ?: 0.0) - (previousMonthLastMileage ?: 0.0)
        val average = if (totalFuelInMonth > 0) mileageIncrease / totalFuelInMonth else null

        val fuelRecords = if (isFirstRecordEver) monthly.drop(1) else monthly
        val hasEstimated = fuelRecords.any { it.isEstimated }

        return MonthlyStats(
            records = monthly,
            recordsWithMileage = recordsWithMileage,
            totalMileage = mileageIncrease,
            totalFuel = totalFuelInMonth,
            averageEfficiency = average,
            previousMonthLastMileage = previousMonthLastMileage,
            previousMonthLastDate = previousMonthLastDate,
            isFirstRecordEver = isFirstRecordEver,
            fuelRecords = fuelRecords,
            hasEstimated = hasEstimated
        )
    }
}

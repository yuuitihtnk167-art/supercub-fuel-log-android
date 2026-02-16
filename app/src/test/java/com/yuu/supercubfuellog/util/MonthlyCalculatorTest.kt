package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.FuelRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyCalculatorTest {
    @Test
    fun calculate_whenFirstRecordEver_excludesFirstFuelFromMonthlyTotal() {
        val records = listOf(
            FuelRecord(id = "1", date = "2025-01-10", mileage = 1000.0, fuel = 4.0),
            FuelRecord(id = "2", date = "2025-01-20", mileage = 1100.0, fuel = 5.0)
        )

        val stats = MonthlyCalculator.calculate(records, "2025-01")

        requireNotNull(stats)
        assertEquals(true, stats.isFirstRecordEver)
        assertEquals(100.0, stats.totalMileage, 1e-9)
        assertEquals(5.0, stats.totalFuel, 1e-9)
        assertEquals(20.0, stats.averageEfficiency ?: 0.0, 1e-9)
    }

    @Test
    fun calculate_withSingleMileageRecord_returnsNoAverage() {
        val records = listOf(
            FuelRecord(id = "1", date = "2025-02-03", mileage = 1500.0, fuel = 3.0),
            FuelRecord(id = "2", date = "2025-02-10", mileage = null, fuel = 2.5)
        )

        val stats = MonthlyCalculator.calculate(records, "2025-02")

        requireNotNull(stats)
        assertNull(stats.averageEfficiency)
        assertEquals(0.0, stats.totalMileage, 1e-9)
        assertEquals(0.0, stats.totalFuel, 1e-9)
    }
}

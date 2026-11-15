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

    @Test
    fun calculate_excludesAllFuelThroughFirstKnownMileage() {
        val records = listOf(
            FuelRecord("before1", "2025-01-01", null, 1.0, isEstimated = true),
            FuelRecord("before2", "2025-01-02", null, 3.0),
            FuelRecord("baseline", "2025-01-03", 1000.0, 4.0, isEstimated = true),
            FuelRecord("between", "2025-01-04", null, 2.0),
            FuelRecord("last", "2025-01-05", 1200.0, 3.0)
        )
        val stats = requireNotNull(MonthlyCalculator.calculate(records.reversed(), "2025-01"))

        assertEquals(listOf("between", "last"), stats.fuelRecords.map { it.id })
        assertEquals(5, stats.records.size)
        assertEquals(200.0, stats.totalMileage, 1e-9)
        assertEquals(5.0, stats.totalFuel, 1e-9)
        assertEquals(40.0, stats.averageEfficiency!!, 1e-9)
        assertEquals(false, stats.hasEstimated)
    }

    @Test
    fun calculate_withPreviousMonthBaselineIncludesAllCurrentMonthFuel() {
        val records = listOf(
            FuelRecord("previous", "2024-12-31", 1000.0, 4.0),
            FuelRecord("before", "2025-01-01", null, 1.0, isEstimated = true),
            FuelRecord("first", "2025-01-02", 1100.0, 2.0),
            FuelRecord("last", "2025-01-31", 1200.0, 2.0),
            FuelRecord("next", "2025-02-01", 1300.0, 10.0)
        )
        val stats = requireNotNull(MonthlyCalculator.calculate(records, "2025-01"))

        assertEquals(false, stats.isFirstRecordEver)
        assertEquals("2024-12-31", stats.previousMonthLastDate)
        assertEquals(1000.0, stats.previousMonthLastMileage!!, 1e-9)
        assertEquals(listOf("before", "first", "last"), stats.fuelRecords.map { it.id })
        assertEquals(40.0, stats.averageEfficiency!!, 1e-9)
        assertEquals(true, stats.hasEstimated)
    }

    @Test
    fun calculate_sameDayLeadingUnknownMileageUsesBaselineId() {
        val records = listOf(
            FuelRecord("unknown", "2025-01-01", null, 9.0),
            FuelRecord("baseline", "2025-01-01", 1000.0, 4.0),
            FuelRecord("last", "2025-01-02", 1100.0, 2.0)
        )
        val stats = requireNotNull(MonthlyCalculator.calculate(records, "2025-01"))
        assertEquals(listOf("last"), stats.fuelRecords.map { it.id })
        assertEquals(50.0, stats.averageEfficiency!!, 1e-9)
    }

    @Test
    fun calculate_emptyMonthReturnsNull() {
        assertNull(MonthlyCalculator.calculate(emptyList(), "2025-01"))
    }
}

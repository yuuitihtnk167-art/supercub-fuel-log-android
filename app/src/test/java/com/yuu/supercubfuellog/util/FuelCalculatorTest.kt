package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.FuelRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelCalculatorTest {
    @Test
    fun recalculate_computesFuelEfficiencyBetweenMileageRecords() {
        val records = listOf(
            FuelRecord(id = "1", date = "2025-01-01", mileage = 1000.0, fuel = 4.0, lastUpdated = 1L),
            FuelRecord(id = "2", date = "2025-01-05", mileage = 1100.0, fuel = 5.0, lastUpdated = 2L)
        )

        val result = FuelCalculator.recalculate(records)

        assertEquals(2, result.size)
        assertNull(result[0].fuelEfficiency)
        assertEquals(20.0, result[1].fuelEfficiency ?: 0.0, 1e-9)
    }

    @Test
    fun recalculate_deduplicatesExactRecordsByLastUpdated() {
        val oldRecord = FuelRecord(
            id = "old",
            date = "2025-02-01",
            mileage = 1200.0,
            fuel = 4.2,
            isEstimated = true,
            lastUpdated = 10L
        )
        val newRecord = oldRecord.copy(
            id = "new",
            isEstimated = false,
            lastUpdated = 20L
        )

        val result = FuelCalculator.recalculate(listOf(oldRecord, newRecord))

        assertEquals(1, result.size)
        assertEquals("new", result.first().id)
    }
}

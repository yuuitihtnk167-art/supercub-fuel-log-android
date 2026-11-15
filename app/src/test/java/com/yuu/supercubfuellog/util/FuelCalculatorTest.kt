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
    fun recalculate_sameIdKeepsLatestVersion() {
        val oldRecord = FuelRecord(
            id = "old",
            date = "2025-02-01",
            mileage = 1200.0,
            fuel = 4.2,
            isEstimated = true,
            lastUpdated = 10L
        )
        val newRecord = oldRecord.copy(
            mileage = 1300.0,
            isEstimated = false,
            lastUpdated = 20L
        )

        val result = FuelCalculator.recalculate(listOf(oldRecord, newRecord))

        assertEquals(1, result.size)
        assertEquals("old", result.first().id)
        assertEquals(1300.0, result.first().mileage!!, 1e-9)
    }

    @Test
    fun recalculate_preservesIdenticalValuesWithDifferentIdsInHistoryAndCsv() {
        val first = FuelRecord("first", "2025-02-01", 1200.0, 4.2)
        val second = first.copy(id = "second", lastUpdated = 20L)
        val result = FuelCalculator.recalculate(listOf(first, second))

        assertEquals(listOf("first", "second"), result.map { it.id })
        assertEquals(3, CsvUtils.generateCsv(result).lineSequence().count())
        assertEquals(2, CsvUtils.parseCsv(CsvUtils.generateCsv(result)).size)
    }

    @Test
    fun recalculate_preservesBothMissingMileageRefuelsInInterval() {
        val records = listOf(
            FuelRecord("start", "2025-01-01", 1000.0, 4.0),
            FuelRecord("a", "2025-01-02", null, 2.0),
            FuelRecord("b", "2025-01-02", null, 2.0),
            FuelRecord("end", "2025-01-03", 1200.0, 1.0)
        )
        val result = FuelCalculator.recalculate(records)

        assertEquals(4, result.size)
        assertEquals(40.0, result.last().fuelEfficiency!!, 1e-9)
    }

    @Test
    fun recalculate_zeroDistanceDoesNotCreateEfficiency() {
        val first = FuelRecord("a", "2025-01-01", 1000.0, 2.0)
        val result = FuelCalculator.recalculate(listOf(first, first.copy(id = "b")))
        assertNull(result.last().fuelEfficiency)
    }
}

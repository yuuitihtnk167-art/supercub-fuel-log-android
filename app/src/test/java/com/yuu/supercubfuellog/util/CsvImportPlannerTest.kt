package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.CsvRecord
import com.yuu.supercubfuellog.data.FuelRecord
import org.junit.Assert.*
import org.junit.Test

class CsvImportPlannerTest {
    private val first = CsvRecord("2026-01-01", 1000.0, 3.0)
    private val second = CsvRecord("2026-01-02", null, 2.5)

    @Test fun repeatedCsvAddsNothingAndKeepsExistingRecords() {
        val existing = FuelRecord("manual-id", first.date, first.mileage, first.fuel, lastUpdated = 123)
        val plan = CsvImportPlanner.plan(listOf(existing), listOf(first, second, second))
        assertEquals(3, plan.validCount)
        assertEquals(2, plan.duplicateCount)
        assertEquals(1, plan.additions.size)
        val afterImport = listOf(existing) + plan.additions
        val retry = CsvImportPlanner.plan(afterImport, listOf(first, second, second))
        assertEquals(0, retry.additions.size)
        assertEquals(3, retry.duplicateCount)
        assertEquals(existing, afterImport.first())
    }

    @Test fun partialImportRetryOnlyAddsMissingRows() {
        val plan = CsvImportPlanner.plan(emptyList(), listOf(first, second))
        val retry = CsvImportPlanner.plan(plan.additions.take(1), listOf(first, second))
        assertEquals(plan.additions.last().id, retry.additions.single().id)
    }

    @Test fun editedImportedRecordIsNeverOverwrittenByReimport() {
        val imported = CsvImportPlanner.plan(emptyList(), listOf(first)).additions.single()
        val edited = imported.copy(fuel = 4.0, lastUpdated = 456)
        val retry = CsvImportPlanner.plan(listOf(edited), listOf(first))
        assertTrue(retry.additions.isEmpty())
        assertEquals(4.0, edited.fuel, 0.0)
    }

    @Test fun rowOrderDoesNotChangeIdsAndDifferentFuelIsDistinct() {
        val forward = CsvImportPlanner.plan(emptyList(), listOf(first, second))
        val reverse = CsvImportPlanner.plan(emptyList(), listOf(second, first))
        assertEquals(forward.additions.map { it.id }.toSet(), reverse.additions.map { it.id }.toSet())
        val distinct = CsvImportPlanner.plan(forward.additions, listOf(first.copy(fuel = 3.1)))
        assertEquals(1, distinct.additions.size)
    }

    @Test fun exportImportRoundTripProducesNoAdditionalRecords() {
        val existing = CsvImportPlanner.plan(emptyList(), listOf(first, second)).additions
        val parsed = CsvUtils.parseDetailed(CsvUtils.generateCsv(existing))
        assertEquals(0, parsed.invalidCount)
        assertTrue(CsvImportPlanner.plan(existing, parsed.records).additions.isEmpty())
    }

    @Test fun malformedRowsAreCountedAndNumericDateRepresentationsAreNormalized() {
        val csv = """
            日付,走行距離(km),給油量(L)
            2026/1/1,"1,000.0",3.00
            2026-02-30,1,3
            2026-01-02,-1,3
            2026-01-02,1,NaN
            2026-01-02,1,Infinity
            2026-01-02,,2.5
        """.trimIndent()
        val result = CsvUtils.parseDetailed("\uFEFF" + csv)
        assertEquals(4, result.invalidCount)
        assertEquals(listOf(first, second), result.records)
    }

    @Test fun csvDuplicatesDoNotDependOnDecimalFormatting() {
        val rows = CsvUtils.parseCsv("date,mileage,fuel\n2026/1/1,1000,3\n2026-01-01,1000.0,3.00")
        val plan = CsvImportPlanner.plan(emptyList(), rows)
        assertEquals(1, plan.additions.size)
        assertEquals(1, plan.duplicateCount)
    }
}

package com.yuu.supercubfuellog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordNumberValidatorTest {
    // Manual input calls the shared validator; CSV and JSON must make the same numeric decision.
    private fun assertAccepted(mileage: String, fuel: String) {
        assertTrue(RecordNumberValidator.validate(mileage, true).isNotEmpty())
        assertTrue(RecordNumberValidator.validate(fuel, false).isNotEmpty())
        val csv = CsvUtils.parseDetailed("date,mileage,fuel\n2025-01-01,$mileage,$fuel")
        assertEquals(0, csv.invalidCount)
        assertEquals(1, csv.records.size)
        assertTrue(AnalysisJsonParser.parse(
            "{\"mileage_km\":$mileage,\"fuel_l\":$fuel}"
        ) is AnalysisJsonResult.Valid)
    }

    private fun assertRejected(raw: String, isMileage: Boolean) {
        assertTrue("$raw must be rejected", runCatching {
            RecordNumberValidator.validate(raw, isMileage)
        }.exceptionOrNull() is IllegalArgumentException)
        val mileage = if (isMileage) raw else "1000"
        val fuel = if (isMileage) "2.13" else raw
        val csv = CsvUtils.parseDetailed("date,mileage,fuel\n2025-01-01,$mileage,$fuel")
        assertEquals(raw, 1, csv.invalidCount)
        assertTrue(csv.records.isEmpty())
        assertTrue(AnalysisJsonParser.parse(
            "{\"mileage_km\":$mileage,\"fuel_l\":$fuel}"
        ) is AnalysisJsonResult.Invalid)
    }

    @Test
    fun acceptsInclusiveLimitsAndDecimalValues() {
        assertAccepted("0", "0.13")
        assertAccepted("1000000000000", "1000000")
        assertAccepted("68982.6", "2.13")
        assertAccepted("1e3", "2.13e0")
    }

    @Test
    fun rejectsValuesAboveLimitsBeforeDoubleRounding() {
        listOf("1000000000001", "1000000000000.0000000001").forEach { assertRejected(it, true) }
        listOf("1000001", "1000000.000000000000001").forEach { assertRejected(it, false) }
    }

    @Test
    fun rejectsNegativeValuesAndZeroFuel() {
        assertRejected("-0.1", true)
        listOf("-0.1", "0", "-0").forEach { assertRejected(it, false) }
    }

    @Test
    fun rejectsNonFiniteAndUnrepresentableValues() {
        listOf("NaN", "Infinity", "-Infinity", "1e309", "1e-400", "invalid").forEach {
            assertRejected(it, true)
            assertRejected(it, false)
        }
    }

    @Test
    fun keepsExactDecimalTextAndBoundsExtremeZeroExponent() {
        assertEquals("0.1234567890123456789",
            RecordNumberValidator.validate("0.1234567890123456789", false))
        assertEquals("0", RecordNumberValidator.validate("0e-999999", true))
    }

    @Test
    fun csvKeepsBlankMileageAndExistingUnitFormatting() {
        val csv = CsvUtils.parseDetailed(
            "date,mileage,fuel\n2025-01-01,,2.13 L\n2025-01-02,\"1,000 km\",0.13"
        )
        assertEquals(0, csv.invalidCount)
        assertEquals(null, csv.records[0].mileage)
        assertEquals(1000.0, csv.records[1].mileage!!, 0.0)
    }
}

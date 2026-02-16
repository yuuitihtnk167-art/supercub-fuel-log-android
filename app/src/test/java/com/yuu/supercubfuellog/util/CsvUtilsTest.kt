package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.FuelRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvUtilsTest {
    @Test
    fun parseCsv_parsesJapaneseHeaderAndNormalizesDate() {
        val csv = """
            日付,走行距離(km),給油量(L)
            2025/02/01,1234.5,3.21
        """.trimIndent()

        val result = CsvUtils.parseCsv(csv)

        assertEquals(1, result.size)
        assertEquals("2025-02-01", result.first().date)
        assertEquals(1234.5, result.first().mileage ?: 0.0, 1e-9)
        assertEquals(3.21, result.first().fuel, 1e-9)
    }

    @Test
    fun generateCsv_outputsExpectedHeader() {
        val records = listOf(
            FuelRecord(
                id = "1",
                date = "2025-02-01",
                mileage = 1300.0,
                fuel = 3.6,
                fuelEfficiency = 35.0
            )
        )

        val csv = CsvUtils.generateCsv(records)
        val firstLine = csv.lineSequence().first()

        assertEquals("日付,走行距離(km),給油量(L),燃費(km/L)", firstLine)
        assertTrue(csv.contains("2025-02-01"))
    }
}

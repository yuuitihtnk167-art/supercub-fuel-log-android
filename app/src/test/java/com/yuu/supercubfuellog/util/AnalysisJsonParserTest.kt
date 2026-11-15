package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.RecordDraft
import org.junit.Assert.*
import org.junit.Test

class AnalysisJsonParserTest {
    private val draft = RecordDraft("record-1", "2026-11-15", "123.4", "5.6", true)

    private fun valid(json: String): AnalysisValues {
        val result = AnalysisJsonParser.parse(json)
        assertTrue(result.toString(), result is AnalysisJsonResult.Valid)
        return (result as AnalysisJsonResult.Valid).values
    }

    private fun invalid(json: String) {
        val result = AnalysisJsonParser.parse(json)
        assertTrue("Must reject: $json -> $result", result is AnalysisJsonResult.Invalid)
        assertTrue((result as AnalysisJsonResult.Invalid).message.isNotBlank())
    }

    @Test fun preservesExpectedDecimals() {
        val values = valid("""{"mileage_km":68982.6,"fuel_l":0.13}""")
        assertEquals("68982.6", values.mileage)
        assertEquals("0.13", values.fuel)
    }

    @Test fun preservesDecimalPrecisionAndReceiptTotal() {
        assertEquals("2.13", valid("""{"fuel_l":2.13}""").fuel)
        assertEquals("0.1234567890123456789", valid("""{"fuel_l":0.1234567890123456789}""").fuel)
    }

    @Test fun acceptsMarkdownFences() {
        for (language in listOf("", "json", "JSON")) {
            assertEquals("0.13", valid("  ```$language\n{\"fuel_l\":0.13}\n```  ").fuel)
        }
        assertEquals("0.13", valid("```json\r\n{\"fuel_l\":0.13}\r\n```").fuel)
    }

    @Test fun normalizesExponentWithoutLosingDecimal() {
        assertEquals("68982.6", valid("""{"mileage_km":6.89826e4}""").mileage)
        assertEquals("0.13", valid("""{"fuel_l":13e-2}""").fuel)
    }

    @Test fun nullAndMissingKeysPreserveExistingInputs() {
        for (json in listOf("{}", """{"mileage_km":null}""", """{"fuel_l":null}""",
            """{"mileage_km":null,"fuel_l":null}""")) {
            assertEquals(draft, valid(json).applyTo(draft))
        }
    }

    @Test fun updatesOnlyProvidedField() {
        assertEquals(draft.copy(fuel = "0.13"), valid("""{"fuel_l":0.13}""").applyTo(draft))
        assertEquals(draft.copy(mileage = "0"), valid("""{"mileage_km":0,"fuel_l":null}""").applyTo(draft))
    }

    @Test fun applyingBothKeepsDateIdAndEditingFlag() {
        val updated = valid("""{"mileage_km":68982.6,"fuel_l":0.13}""").applyTo(draft)
        assertEquals(draft.copy(mileage = "68982.6", fuel = "0.13"), updated)
        assertEquals("123.4", draft.mileage)
        assertEquals("5.6", draft.fuel)
    }

    @Test fun acceptsFirestoreBoundaryValues() {
        assertEquals("1000000000000", valid("""{"mileage_km":1000000000000}""").mileage)
        assertEquals("1000000", valid("""{"fuel_l":1000000}""").fuel)
        assertEquals("0", valid("""{"mileage_km":-0.0}""").mileage)
    }

    @Test fun rejectsOutsideRangesIncludingDecimalOverflow() {
        for (value in listOf("-1", "1000000000000.0001", "1e13")) invalid("""{"mileage_km":$value}""")
        for (value in listOf("0", "-0.0", "-1", "1000000.0000000001", "1e7")) invalid("""{"fuel_l":$value}""")
    }

    @Test fun rejectsWrongTypesWithoutCoercion() {
        for (key in listOf("mileage_km", "fuel_l")) {
            for (value in listOf("\"0.13\"", "true", "false", "[]", "{}")) invalid("""{"$key":$value}""")
        }
    }

    @Test fun rejectsNonFiniteAndUnrepresentableNumbers() {
        for (key in listOf("mileage_km", "fuel_l")) {
            for (value in listOf("NaN", "Infinity", "-Infinity", "1e9999", "1e-9999",
                "\"NaN\"", "\"Infinity\"")) invalid("""{"$key":$value}""")
        }
    }

    @Test fun rejectsMalformedAndLenientJson() {
        for (json in listOf("", "null", "[]", "0.13", "{", "{fuel_l:0.13}", "{'fuel_l':0.13}",
            """{"fuel_l":0.13,}""", """{"fuel_l":+1}""", """{"fuel_l":01}""",
            """{"fuel_l":.13}""", """{"fuel_l":NULL}""", """{/*comment*/"fuel_l":1}""",
            """{"fuel_l":1} trailing""", """{"fuel_l":1}{"fuel_l":2}""")) invalid(json)
    }

    @Test fun rejectsIncompleteFencesAndSurroundingExplanations() {
        for (json in listOf("```json\n{\"fuel_l\":1}", "説明\n{\"fuel_l\":1}",
            "```javascript\n{\"fuel_l\":1}\n```", "```json\n{\"fuel_l\":1}\n```\n説明")) invalid(json)
    }

    @Test fun rejectsDuplicateAndUnknownKeys() {
        invalid("""{"fuel_l":1,"fuel_l":2}""")
        invalid("""{"mileage_km":1,"mileage_km":null}""")
        invalid("""{"fuel_l":1,"date":"2020-01-01"}""")
        invalid("""{"mileage":1}""")
    }

    @Test fun invalidSecondFieldNeverProducesAPartialPatch() {
        invalid("""{"mileage_km":68982.6,"fuel_l":-1}""")
        invalid("""{"fuel_l":0.13,"mileage_km":"invalid"}""")
        assertEquals(RecordDraft("record-1", "2026-11-15", "123.4", "5.6", true), draft)
    }

    @Test fun rejectsOversizedInputAndHandlesExtremeZeroExponent() {
        invalid(" ".repeat(AnalysisJsonParser.MAX_TEXT_LENGTH + 1))
        assertEquals("0", valid("""{"mileage_km":0e-999999}""").mileage)
    }
}

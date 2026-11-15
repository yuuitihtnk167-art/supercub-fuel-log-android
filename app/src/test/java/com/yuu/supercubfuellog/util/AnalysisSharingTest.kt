package com.yuu.supercubfuellog.util

import com.yuu.supercubfuellog.data.DraftPhoto
import com.yuu.supercubfuellog.data.PhotoDraftState
import com.yuu.supercubfuellog.data.PhotoField
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class AnalysisSharingTest {
    private fun photo(name: String) = DraftPhoto("$name.jpg", name)
    private val photos = PhotoDraftState(
        meter = listOf(photo("meter")),
        receipts = listOf(photo("receipt1"), photo("receipt2"))
    )
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun zeroImagesCannotStartShare() {
        rejected { AnalysisSharePlan.create("user", "record", PhotoDraftState()) }
    }

    @Test fun anySingleImageUsesSingleShareAndHasBothOutputKeys() {
        for (state in listOf(PhotoDraftState(meter = listOf(photo("m"))),
            PhotoDraftState(receipts = listOf(photo("r"))))) {
            val plan = AnalysisSharePlan.create("user", "record", state)
            assertFalse(plan.multiple)
            assertEquals(1, plan.photoFiles.size)
            assertTrue(plan.instructions.contains("mileage_km"))
            assertTrue(plan.instructions.contains("fuel_l"))
            assertTrue(plan.instructions.contains("null"))
        }
    }

    @Test fun twoAndThreeImagesUseMultipleShareWithoutDroppingReceipts() {
        val two = AnalysisSharePlan.create("user", "record", photos.copy(meter = emptyList()))
        assertTrue(two.multiple)
        assertEquals(listOf("receipt1.jpg", "receipt2.jpg"), two.photoFiles)
        val three = AnalysisSharePlan.create("user", "record", photos)
        assertTrue(three.multiple)
        assertEquals(listOf("meter.jpg", "receipt1.jpg", "receipt2.jpg"), three.photoFiles)
    }

    @Test fun imageLimitsDuplicatesAndPendingAcquisitionAreRejected() {
        rejected { AnalysisSharePlan.create("u", "r", photos.copy(meter = listOf(photo("m1"), photo("m2")))) }
        rejected { AnalysisSharePlan.create("u", "r", photos.copy(receipts = listOf(photo("1"), photo("2"), photo("3")))) }
        rejected { AnalysisSharePlan.create("u", "r", PhotoDraftState(receipts = listOf(photo("same"), photo("same")))) }
        rejected { AnalysisSharePlan.create("u", "r", photos.copy(processing = true)) }
        val pending = photos.copy(receipts = listOf(photo("1"))).begin("u", "r", PhotoField.RECEIPT, null, null)
        rejected { AnalysisSharePlan.create("u", "r", pending) }
    }

    @Test fun promptIncludesCountsAndAllOrNothingReceiptTotalRule() {
        val text = AnalysisSharePlan.create("user", "record", photos).instructions
        assertTrue(text.contains("メーター1枚、レシート2枚"))
        assertTrue(text.contains("合算"))
        assertTrue(text.contains("一方でも読めない"))
        assertTrue(text.contains("fuel_lをnull"))
        assertTrue(text.contains("小数第1位"))
        assertTrue(text.contains("推測で埋めない"))
        assertTrue(text.contains("写真の内容"))
        assertTrue(text.contains("重複"))
        assertTrue(text.contains("日付など他のキーは出力しない"))
        assertTrue(text.contains("MarkdownのJSONコードブロック1つだけ"))
        val fencedExample = text.substring(text.indexOf("\n```json\n") + 1)
        val parsed = AnalysisJsonParser.parse(fencedExample) as AnalysisJsonResult.Valid
        assertEquals("68982.6", parsed.values.mileage)
        assertEquals("2.13", parsed.values.fuel)
    }

    @Test fun promptTracksMissingPhotoCategory() {
        assertTrue(AnalysisInstructions.create(0, 1).contains("メーター0枚、レシート1枚"))
        assertTrue(AnalysisInstructions.create(1, 0).contains("メーター1枚、レシート0枚"))
        rejected { AnalysisInstructions.create(2, 0) }
        rejected { AnalysisInstructions.create(0, 3) }
    }

    @Test fun openingShareMovesInitialStateToPasteWaitingForCurrentRecord() {
        val plan = AnalysisSharePlan.create("user", "record", photos)
        val flow = AnalysisFlowState().shareOpened(plan)
        assertTrue(flow.awaitingPaste)
        assertEquals(flow, flow.forContext("user", "record", plan.photoFiles))
        // This state says nothing about delivery, analysis, or the contents of the clipboard.
        assertEquals("user", flow.ownerUid)
    }

    @Test fun accountRecordAndPhotoChangesDiscardOldPasteWaiting() {
        val plan = AnalysisSharePlan.create("user", "record", photos)
        val flow = AnalysisFlowState().shareOpened(plan)
        assertFalse(flow.forContext("other-user", "record", plan.photoFiles).awaitingPaste)
        assertFalse(flow.forContext(null, "record", plan.photoFiles).awaitingPaste)
        assertFalse(flow.forContext("user", "other-record", plan.photoFiles).awaitingPaste)
        assertFalse(flow.forContext("user", "record", plan.photoFiles.dropLast(1)).awaitingPaste)
        assertFalse(flow.forContext("user", "record", listOf("replacement.jpg")).awaitingPaste)
    }

    @Test fun confirmingPasteResetsWaitingAndRetryCanOpenAgain() {
        val plan = AnalysisSharePlan.create("user", "record", photos)
        val flow = AnalysisFlowState().shareOpened(plan)
        assertEquals(AnalysisFlowState(), flow.pasteConfirmed())
        assertTrue(flow.pasteConfirmed().shareOpened(plan).awaitingPaste)
        assertEquals(flow, flow.shareOpened(plan))
    }

    @Test fun processStateRoundTripPreservesPasteWaiting() {
        val plan = AnalysisSharePlan.create("user", "record", photos)
        val flow = AnalysisFlowState().shareOpened(plan)
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(flow) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as AnalysisFlowState }
        assertEquals(flow, restored.forContext("user", "record", plan.photoFiles))
    }
}

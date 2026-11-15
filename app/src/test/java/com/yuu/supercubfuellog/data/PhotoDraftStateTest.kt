package com.yuu.supercubfuellog.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class PhotoDraftStateTest {
    private fun photo(id: String) = DraftPhoto("$id.jpg", id)
    private fun add(state: PhotoDraftState, field: PhotoField, vararg photos: DraftPhoto): PhotoDraftState {
        val next = state.begin("user-a", "record-a", field, null, null)
        return next.complete(next.pending!!, photos.toList(), "user-a", "record-a")
    }
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun keepsMeterAndReceiptsSeparate() {
        val state = add(add(PhotoDraftState(), PhotoField.METER, photo("m")), PhotoField.RECEIPT, photo("r1"), photo("r2"))
        assertEquals(listOf(photo("m")), state.meter)
        assertEquals(listOf(photo("r1"), photo("r2")), state.receipts)
    }

    @Test fun addingSecondReceiptKeepsFirst() {
        val state = add(add(PhotoDraftState(), PhotoField.RECEIPT, photo("r1")), PhotoField.RECEIPT, photo("r2"))
        assertEquals(listOf(photo("r1"), photo("r2")), state.receipts)
    }

    @Test fun secondReceiptRequestAddsWithoutReplacingFirstForPickerAndCamera() {
        val initial = add(add(PhotoDraftState(), PhotoField.METER, photo("meter")), PhotoField.RECEIPT, photo("first"))
        for (cameraFile in listOf(null, "capture.jpg")) {
            val waiting = initial.begin("user-a", "record-a", PhotoField.RECEIPT, null, cameraFile)
            val request = waiting.pending!!
            assertNull(request.replaceFileName)
            val result = waiting.complete(request, listOf(photo("second")), "user-a", "record-a")
            assertEquals(listOf(photo("first"), photo("second")), result.receipts)
            assertEquals(initial.meter, result.meter)
        }
    }

    @Test fun cancellingSecondReceiptSelectionOrCapturePreservesAllExistingPhotos() {
        val initial = add(add(PhotoDraftState(), PhotoField.METER, photo("meter")), PhotoField.RECEIPT, photo("first"))
        for (cameraFile in listOf(null, "capture.jpg")) {
            val waiting = initial.begin("user-a", "record-a", PhotoField.RECEIPT, null, cameraFile)
            val request = waiting.pending!!
            assertEquals(initial, waiting.cancel(request))
            assertEquals(initial, waiting.complete(request, emptyList(), "user-a", "record-a"))
        }
    }

    @Test fun secondReceiptCanBeReplacedAndDeletedWithoutChangingFirst() {
        val initial = add(PhotoDraftState(), PhotoField.RECEIPT, photo("first"), photo("second"))
        val waiting = initial.begin("user-a", "record-a", PhotoField.RECEIPT, "second.jpg", null)
        val result = waiting.complete(waiting.pending!!, listOf(photo("replacement")), "user-a", "record-a")
        assertEquals(listOf(photo("first"), photo("replacement")), result.receipts)
        assertEquals(listOf(photo("first")), result.remove(PhotoField.RECEIPT, "replacement.jpg").receipts)
    }

    @Test fun cannotStartAdditionWhenFull() {
        val receipts = add(PhotoDraftState(), PhotoField.RECEIPT, photo("a"), photo("b"))
        rejected { receipts.begin("user-a", "record-a", PhotoField.RECEIPT, null, null) }
        val meter = add(PhotoDraftState(), PhotoField.METER, photo("m"))
        rejected { meter.begin("user-a", "record-a", PhotoField.METER, null, null) }
    }

    @Test fun rejectsOversizedSelectionAtomically() {
        val initial = add(PhotoDraftState(), PhotoField.RECEIPT, photo("a"))
        val waiting = initial.begin("user-a", "record-a", PhotoField.RECEIPT, null, null)
        rejected { waiting.complete(waiting.pending!!, listOf(photo("b"), photo("c")), "user-a", "record-a") }
        assertEquals(initial.receipts, waiting.receipts)
    }

    @Test fun cancellationKeepsExistingPhotos() {
        val initial = add(PhotoDraftState(), PhotoField.METER, photo("m"))
        val waiting = initial.begin("user-a", "record-a", PhotoField.METER, "m.jpg", "capture.jpg")
        assertEquals(initial, waiting.cancel(waiting.pending!!))
        assertEquals(initial, waiting.complete(waiting.pending!!, emptyList(), "user-a", "record-a"))
    }

    @Test fun replacesOnlyRequestedReceipt() {
        val initial = add(PhotoDraftState(), PhotoField.RECEIPT, photo("a"), photo("b"))
        val waiting = initial.begin("user-a", "record-a", PhotoField.RECEIPT, "a.jpg", null)
        val next = waiting.complete(waiting.pending!!, listOf(photo("c")), "user-a", "record-a")
        assertEquals(listOf(photo("c"), photo("b")), next.receipts)
        assertNull(next.pending)
    }

    @Test fun rejectsMissingReplacementAndMultipleReplacementImages() {
        val initial = add(PhotoDraftState(), PhotoField.RECEIPT, photo("a"))
        rejected { initial.begin("user-a", "record-a", PhotoField.RECEIPT, "missing.jpg", null) }
        val waiting = initial.begin("user-a", "record-a", PhotoField.RECEIPT, "a.jpg", null)
        rejected { waiting.complete(waiting.pending!!, listOf(photo("b"), photo("c")), "user-a", "record-a") }
    }

    @Test fun duplicateContentIsRejectedEvenWithDifferentFileNames() {
        val initial = add(PhotoDraftState(), PhotoField.RECEIPT, photo("a"))
        val waiting = initial.begin("user-a", "record-a", PhotoField.RECEIPT, null, null)
        rejected { waiting.complete(waiting.pending!!, listOf(DraftPhoto("different.jpg", "a")), "user-a", "record-a") }
        assertEquals(initial.receipts, waiting.receipts)
    }

    @Test fun duplicateWithinBatchIsRejected() {
        val waiting = PhotoDraftState().begin("user-a", "record-a", PhotoField.RECEIPT, null, null)
        rejected { waiting.complete(waiting.pending!!, listOf(photo("a"), DraftPhoto("b.jpg", "a")), "user-a", "record-a") }
        assertTrue(waiting.receipts.isEmpty())
    }

    @Test fun replacementMayUseSameContent() {
        val initial = add(PhotoDraftState(), PhotoField.METER, photo("m"))
        val waiting = initial.begin("user-a", "record-a", PhotoField.METER, "m.jpg", null)
        val next = waiting.complete(waiting.pending!!, listOf(DraftPhoto("new.jpg", "m")), "user-a", "record-a")
        assertEquals("new.jpg", next.meter.single().fileName)
    }

    @Test fun accountRecordAndSessionChangesRejectLateResults() {
        val waiting = PhotoDraftState().begin("user-a", "record-a", PhotoField.METER, null, "capture.jpg")
        val request = waiting.pending!!
        assertFalse(waiting.accepts(request, "user-b", "record-a"))
        assertFalse(waiting.accepts(request, null, "record-a"))
        assertFalse(waiting.accepts(request, "user-a", "record-b"))
        assertFalse(PhotoDraftState().accepts(request, "user-a", "record-a"))
        rejected { waiting.complete(request, listOf(photo("m")), "user-b", "record-a") }
    }

    @Test fun staleCancellationDoesNotCancelNewRequest() {
        val initial = PhotoDraftState()
        val first = initial.begin("user-a", "record-a", PhotoField.METER, null, null)
        val second = first.cancel(first.pending!!).begin("user-a", "record-a", PhotoField.RECEIPT, null, null)
        assertEquals(second, second.cancel(first.pending!!))
        assertFalse(second.accepts(first.pending!!, "user-a", "record-a"))
    }

    @Test fun removalIsIndividualAndFreesCapacity() {
        val initial = add(PhotoDraftState(), PhotoField.RECEIPT, photo("a"), photo("b"))
        val next = initial.remove(PhotoField.RECEIPT, "a.jpg")
        assertEquals(listOf(photo("b")), next.receipts)
        assertEquals(2, add(next, PhotoField.RECEIPT, photo("c")).receipts.size)
    }

    @Test fun secondAcquisitionAndRemovalAreBlockedWhilePending() {
        val waiting = PhotoDraftState().begin("user-a", "record-a", PhotoField.METER, null, null)
        rejected { waiting.begin("user-a", "record-a", PhotoField.RECEIPT, null, null) }
        rejected { waiting.remove(PhotoField.METER, "a.jpg") }
    }

    @Test fun interruptedCopyRecoveryKeepsPriorPhotosAndFreesPendingRequest() {
        val initial = add(PhotoDraftState(), PhotoField.RECEIPT, photo("r"))
        val waiting = initial.begin("user-a", "record-a", PhotoField.RECEIPT, null, null)
        assertEquals(waiting, waiting.recoverInterruptedCopy())
        val recovered = waiting.copy(processing = true).recoverInterruptedCopy()
        assertEquals(initial, recovered)
        assertFalse(recovered.processing)
        assertNull(recovered.pending)
    }

    @Test fun savedStateRoundTripPreservesPhotosAndPendingTarget() {
        val waiting = add(PhotoDraftState(), PhotoField.RECEIPT, photo("r"))
            .begin("user-a", "record-a", PhotoField.METER, null, "capture.jpg")
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(waiting) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as PhotoDraftState }
        assertEquals(waiting, restored)
        assertTrue(restored.accepts(waiting.pending!!, "user-a", "record-a"))
        assertEquals(listOf(photo("r")), restored.receipts)
    }
}

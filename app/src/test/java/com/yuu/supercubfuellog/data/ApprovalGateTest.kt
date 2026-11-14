package com.yuu.supercubfuellog.data

import org.junit.Assert.*
import org.junit.Test

class ApprovalGateTest {
    @Test fun persistedLoginStillRequiresAuthoritativeApproval() {
        val gate = ApprovalGate("owner")
        assertEquals(ApprovalGate.Status.CHECKING, gate.status)
        assertNull(gate.lease())
        val check = gate.beginCheck()!!
        gate.snapshot(check, true, true, fromCache = true)
        assertNull(gate.lease())
        gate.snapshot(check, true, true, fromCache = false)
        assertNotNull(gate.lease())
    }

    @Test fun missingDisabledAndWrongTypesAreNotApproved() {
        for (value in listOf<Any?>(false, null, "true", 1)) {
            val gate = ApprovalGate("owner")
            gate.snapshot(gate.beginCheck()!!, true, value, false)
            assertEquals(ApprovalGate.Status.DENIED, gate.status)
            assertNull(gate.lease())
        }
        val gate = ApprovalGate("owner")
        gate.snapshot(gate.beginCheck()!!, false, true, false)
        assertEquals(ApprovalGate.Status.DENIED, gate.status)
    }

    @Test fun communicationFailureIsNotDenialAndRetryCanApprove() {
        val gate = ApprovalGate("owner")
        val first = gate.beginCheck()!!
        gate.communicationFailure(first)
        assertEquals(ApprovalGate.Status.ERROR, gate.status)
        assertNull(gate.lease())
        val retry = gate.beginCheck()!!
        assertEquals(ApprovalGate.Status.CHECKING, gate.status)
        assertFalse(gate.snapshot(first, true, true, false))
        gate.snapshot(retry, true, true, false)
        assertEquals(ApprovalGate.Status.APPROVED, gate.status)
    }

    @Test fun revocationAndDeletionInvalidateInFlightRecordAndExportLeases() {
        for (exists in listOf(true, false)) {
            val gate = ApprovalGate("owner")
            val check = gate.beginCheck()!!
            gate.snapshot(check, true, true, false)
            val work = gate.lease()!!
            gate.snapshot(check, exists, false, false)
            assertEquals(ApprovalGate.Status.DENIED, gate.status)
            assertFalse(gate.accepts(work))
        }
    }

    @Test fun losingServerConnectionStopsApprovedWorkAndCacheCannotRestoreIt() {
        val gate = ApprovalGate("owner")
        val check = gate.beginCheck()!!
        gate.snapshot(check, true, true, false)
        val work = gate.lease()!!
        gate.snapshot(check, true, true, true)
        assertEquals(ApprovalGate.Status.ERROR, gate.status)
        assertFalse(gate.accepts(work))
        gate.snapshot(check, true, true, true)
        assertNull(gate.lease())
        gate.snapshot(check, true, true, false)
        assertNotNull(gate.lease())
        assertFalse(gate.accepts(work))
    }

    @Test fun accountSwitchRejectsPreviousApprovalDenialAndFailureCallbacks() {
        val gate = ApprovalGate("a")
        val a = gate.beginCheck()!!
        gate.snapshot(a, true, true, false)
        val aWork = gate.lease()!!
        gate.selectAccount("b")
        val b = gate.beginCheck()!!
        assertFalse(gate.snapshot(a, true, true, false))
        assertFalse(gate.snapshot(a, false, false, false))
        assertFalse(gate.communicationFailure(a))
        assertEquals(ApprovalGate.Status.CHECKING, gate.status)
        gate.snapshot(b, true, true, false)
        assertEquals("b", gate.lease()!!.uid)
        assertFalse(gate.accepts(aWork))
    }

    @Test fun logoutAndSameAccountReloginCannotReuseOldApproval() {
        val gate = ApprovalGate("a")
        val old = gate.beginCheck()!!
        gate.snapshot(old, true, true, false)
        val work = gate.lease()!!
        gate.selectAccount(null)
        assertEquals(ApprovalGate.Status.SIGNED_OUT, gate.status)
        assertNull(gate.beginCheck())
        gate.selectAccount("a")
        gate.beginCheck()
        assertFalse(gate.snapshot(old, true, true, false))
        assertFalse(gate.accepts(work))
        assertNull(gate.lease())
    }

    @Test fun foregroundRecheckBlocksWorkBeforeAnyResponse() {
        val gate = ApprovalGate("a")
        val old = gate.beginCheck()!!
        gate.snapshot(old, true, true, false)
        val work = gate.lease()!!
        gate.beginCheck()
        assertEquals(ApprovalGate.Status.CHECKING, gate.status)
        assertFalse(gate.accepts(work))
        assertFalse(gate.snapshot(old, true, true, false))
    }

    @Test fun localPendingWritesCannotGrantApprovalEvenWithServerMetadata() {
        val gate = ApprovalGate("a")
        val check = gate.beginCheck()!!
        gate.snapshot(check, true, true, fromCache = false, pendingWrites = true)
        assertNull(gate.lease())
        gate.snapshot(check, true, true, fromCache = false, pendingWrites = false)
        val lease = gate.lease()!!
        gate.snapshot(check, true, true, fromCache = false, pendingWrites = true)
        assertEquals(ApprovalGate.Status.ERROR, gate.status)
        assertFalse(gate.accepts(lease))
    }
}

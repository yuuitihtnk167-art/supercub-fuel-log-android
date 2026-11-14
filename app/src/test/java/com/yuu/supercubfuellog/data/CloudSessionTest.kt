package com.yuu.supercubfuellog.data

import org.junit.Assert.*
import org.junit.Test

class CloudSessionTest {
    @Test fun signOutRejectsOldResultsEvenAfterSigningBackIntoSameAccount() {
        val session = CloudSession("account-a")
        val old = session.token()!!
        assertTrue(session.accepts(old))
        assertTrue(session.changeUser(null))
        assertNull(session.token())
        assertFalse(session.accepts(old))
        session.changeUser("account-a")
        assertFalse(session.accepts(old))
        assertTrue(session.accepts(session.token()!!))
    }

    @Test fun accountSwitchRejectsPreviousAccountRecordsAndImportPreview() {
        val session = CloudSession("account-a")
        val previous = session.token()!!
        session.changeUser("account-b")
        assertFalse(session.accepts(previous))
        assertEquals("account-b", session.token()!!.uid)
        assertFalse(session.changeUser("account-b"))
        assertTrue(session.accepts(session.token()!!))
    }
}

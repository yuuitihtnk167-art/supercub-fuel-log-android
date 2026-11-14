package com.yuu.supercubfuellog.data

/** Pure authorization state machine. Cached data never creates an approval lease. */
class ApprovalGate(initialUid: String? = null) {
    enum class Status { SIGNED_OUT, CHECKING, APPROVED, ERROR, DENIED }
    data class Check(val uid: String, val account: Int, val request: Int)
    data class Lease(val uid: String, val account: Int, val revision: Int)

    private var uid = initialUid
    private var account = 0
    private var request = 0
    private var revision = 0
    var status = if (initialUid == null) Status.SIGNED_OUT else Status.CHECKING
        private set

    fun selectAccount(newUid: String?) {
        uid = newUid
        account++
        request++
        transition(if (newUid == null) Status.SIGNED_OUT else Status.CHECKING)
    }

    fun beginCheck(): Check? {
        val owner = uid ?: return null
        request++
        // Invalidate all work authorized before this verification, even if already checking.
        revision++
        transition(Status.CHECKING)
        return Check(owner, account, request)
    }

    fun isCurrent(check: Check): Boolean =
        check.uid == uid && check.account == account && check.request == request

    fun snapshot(check: Check, exists: Boolean, enabled: Any?, fromCache: Boolean, pendingWrites: Boolean = false): Boolean {
        if (!isCurrent(check)) return false
        if (fromCache || pendingWrites) {
            if (status == Status.APPROVED) transition(Status.ERROR)
            return true
        }
        transition(if (exists && enabled == true) Status.APPROVED else Status.DENIED)
        return true
    }

    fun communicationFailure(check: Check): Boolean {
        if (!isCurrent(check)) return false
        transition(Status.ERROR)
        return true
    }

    fun lease(): Lease? =
        if (status == Status.APPROVED && uid != null) Lease(uid!!, account, revision) else null

    fun accepts(lease: Lease): Boolean = this.lease() == lease

    private fun transition(next: Status) {
        if (status != next) revision++
        status = next
    }
}

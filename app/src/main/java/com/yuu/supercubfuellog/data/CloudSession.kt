package com.yuu.supercubfuellog.data

/** Identifies one signed-in session, including sign-out and sign-in to the same account. */
class CloudSession(initialUid: String?) {
    data class Token(val uid: String, val generation: Int)
    private var uid = initialUid
    private var generation = 0

    fun changeUser(newUid: String?): Boolean {
        if (uid == newUid) return false
        uid = newUid
        generation++
        return true
    }

    fun token(): Token? = uid?.let { Token(it, generation) }
    fun accepts(token: Token): Boolean = this.token() == token
}

package com.yuu.supercubfuellog.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class ApprovalRequiredException : IllegalStateException("利用には管理者の承認が必要です")

/** Own-document reads only. This class deliberately exposes no ACL write or list API. */
class ApprovalRepository(private val firestore: FirebaseFirestore) {
    private fun document(uid: String) = firestore.collection("allowedUsers").document(uid)

    fun watch(
        uid: String,
        snapshot: (exists: Boolean, enabled: Any?, fromCache: Boolean, pendingWrites: Boolean) -> Unit,
        failure: () -> Unit
    ): ListenerRegistration =
        document(uid).addSnapshotListener(MetadataChanges.INCLUDE) { result, error ->
            if (error != null || result == null) {
                failure()
            } else {
                snapshot(result.exists(), result.get("enabled"), result.metadata.isFromCache, result.metadata.hasPendingWrites())
            }
        }

    suspend fun requireEnabled(uid: String) {
        val result = document(uid).get(Source.SERVER).await()
        check(!result.metadata.hasPendingWrites()) { "承認状態のサーバー確認が完了していません。" }
        if (!result.exists() || result.get("enabled") != true) throw ApprovalRequiredException()
    }
}

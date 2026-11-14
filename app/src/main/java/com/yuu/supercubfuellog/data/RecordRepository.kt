package com.yuu.supercubfuellog.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/** All normal app data is scoped to the explicitly captured Firebase UID. */
class RecordRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private fun collection(uid: String) =
        firestore.collection("users").document(uid).collection("records")

    private fun checkUser(uid: String) {
        check(auth.currentUser?.uid == uid) { "ログイン状態が変わりました。ログインし直してください。" }
    }

    suspend fun load(uid: String): List<FuelRecord> {
        checkUser(uid)
        return collection(uid).get(Source.SERVER).await().documents.mapNotNull { doc ->
            val date = doc.getString("date") ?: return@mapNotNull null
            FuelRecord(
                id = doc.id,
                date = date,
                mileage = doc.getDouble("mileage"),
                fuel = doc.getDouble("fuel") ?: 0.0,
                fuelEfficiency = doc.getDouble("fuelEfficiency"),
                isEstimated = doc.getBoolean("isEstimated") ?: false,
                lastUpdated = doc.getLong("lastUpdated")
            )
        }
    }

    private fun data(record: FuelRecord): Map<String, Any?> = mapOf(
        "date" to record.date,
        "mileage" to record.mileage,
        "fuel" to record.fuel,
        "fuelEfficiency" to record.fuelEfficiency,
        "isEstimated" to record.isEstimated,
        "lastUpdated" to record.lastUpdated
    )

    suspend fun save(uid: String, record: FuelRecord) {
        checkUser(uid)
        val ref = collection(uid).document(record.id)
        // Transactions fail offline instead of queuing an unacknowledged offline write.
        firestore.runTransaction { transaction ->
            checkUser(uid)
            transaction.get(ref)
            transaction.set(ref, data(record))
        }.await()
    }

    suspend fun delete(uid: String, id: String) {
        checkUser(uid)
        val ref = collection(uid).document(id)
        firestore.runTransaction { transaction ->
            checkUser(uid)
            transaction.get(ref)
            transaction.delete(ref)
        }.await()
    }

    suspend fun deleteAll(uid: String) {
        val records = load(uid)
        records.chunked(200).forEach { chunk ->
            checkUser(uid)
            firestore.runTransaction { transaction ->
                checkUser(uid)
                val refs = chunk.map { collection(uid).document(it.id) }
                refs.forEach { transaction.get(it) }
                refs.forEach { transaction.delete(it) }
            }.await()
        }
    }

    /** Never overwrites an existing document, even after a timeout or concurrent retry. */
    suspend fun importNew(uid: String, records: List<FuelRecord>) {
        records.chunked(200).forEach { chunk ->
            checkUser(uid)
            firestore.runTransaction { transaction ->
                checkUser(uid)
                val missing = chunk.map { collection(uid).document(it.id) to it }
                    .filter { (ref, _) -> !transaction.get(ref).exists() }
                missing.forEach { (ref, record) -> transaction.set(ref, data(record)) }
            }.await()
        }
    }
}

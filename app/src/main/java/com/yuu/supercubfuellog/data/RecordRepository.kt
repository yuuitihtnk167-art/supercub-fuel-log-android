package com.yuu.supercubfuellog.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RecordRepository(
    private val dao: RecordDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    suspend fun load(source: DataSource): List<FuelRecord> {
        return when (source) {
            DataSource.LOCAL -> dao.getAll().map { it.toDomain() }
            DataSource.CLOUD -> {
                val uid = requireUid()
                val snapshot = firestore.collection("users")
                    .document(uid)
                    .collection("records")
                    .get()
                    .await()
                snapshot.documents.mapNotNull { doc ->
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
        }
    }

    suspend fun replaceAll(source: DataSource, records: List<FuelRecord>) {
        when (source) {
            DataSource.LOCAL -> {
                dao.deleteAll()
                dao.insertAll(records.map { it.toEntity() })
            }
            DataSource.CLOUD -> {
                val uid = requireUid()
                replaceCloud(uid, records)
            }
        }
    }

    suspend fun deleteAll(source: DataSource) {
        when (source) {
            DataSource.LOCAL -> dao.deleteAll()
            DataSource.CLOUD -> {
                val uid = requireUid()
                replaceCloud(uid, emptyList())
            }
        }
    }

    private suspend fun replaceCloud(uid: String, records: List<FuelRecord>) {
        val collection = firestore.collection("users")
            .document(uid)
            .collection("records")

        val existingIds = collection.get().await().documents.map { it.id }.toSet()
        val incomingIds = records.map { it.id }.toSet()
        val toDelete = existingIds.subtract(incomingIds)

        val operations = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()

        records.forEach { record ->
            val docRef = collection.document(record.id)
            val data = mapOf(
                "date" to record.date,
                "mileage" to record.mileage,
                "fuel" to record.fuel,
                "fuelEfficiency" to record.fuelEfficiency,
                "isEstimated" to record.isEstimated,
                "lastUpdated" to record.lastUpdated
            )
            operations.add { batch -> batch.set(docRef, data) }
        }

        toDelete.forEach { id ->
            val docRef = collection.document(id)
            operations.add { batch -> batch.delete(docRef) }
        }

        operations.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { it(batch) }
            batch.commit().await()
        }
    }

    private fun requireUid(): String {
        return auth.currentUser?.uid ?: error("User not signed in")
    }
}

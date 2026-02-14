package com.yuu.supercubfuellog.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "records")
data class LocalRecordEntity(
    @PrimaryKey val id: String,
    val date: String,
    val mileage: Double?,
    val fuel: Double,
    val fuelEfficiency: Double?,
    val isEstimated: Boolean,
    val lastUpdated: Long?
)

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY date ASC")
    suspend fun getAll(): List<LocalRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<LocalRecordEntity>)

    @Query("DELETE FROM records")
    suspend fun deleteAll()
}

@Database(entities = [LocalRecordEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
}

fun LocalRecordEntity.toDomain(): FuelRecord = FuelRecord(
    id = id,
    date = date,
    mileage = mileage,
    fuel = fuel,
    fuelEfficiency = fuelEfficiency,
    isEstimated = isEstimated,
    lastUpdated = lastUpdated
)

fun FuelRecord.toEntity(): LocalRecordEntity = LocalRecordEntity(
    id = id,
    date = date,
    mileage = mileage,
    fuel = fuel,
    fuelEfficiency = fuelEfficiency,
    isEstimated = isEstimated,
    lastUpdated = lastUpdated
)

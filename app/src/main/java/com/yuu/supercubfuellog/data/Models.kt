package com.yuu.supercubfuellog.data

enum class DataSource {
    LOCAL,
    CLOUD
}

data class FuelRecord(
    val id: String,
    val date: String,
    val mileage: Double?,
    val fuel: Double,
    val fuelEfficiency: Double? = null,
    val isEstimated: Boolean = false,
    val lastUpdated: Long? = null
)

data class CsvRecord(
    val date: String,
    val mileage: Double?,
    val fuel: Double
)

data class FormulaInfo(
    val prevDate: String,
    val prevMileage: Double,
    val currentMileage: Double,
    val distance: Double,
    val intermediateFuels: List<IntermediateFuel>,
    val currentFuel: Double,
    val currentIsEstimated: Boolean,
    val totalFuel: Double,
    val efficiency: Double
)

data class IntermediateFuel(
    val date: String,
    val fuel: Double,
    val isEstimated: Boolean
)

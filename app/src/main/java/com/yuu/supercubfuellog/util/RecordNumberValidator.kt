package com.yuu.supercubfuellog.util

import java.math.BigDecimal

/** Shared numeric limits for manual input, CSV and analysis JSON, matching firestore.rules. */
object RecordNumberValidator {
    private val maxMileage = BigDecimal("1000000000000")
    private val maxFuel = BigDecimal("1000000")

    /** Returns decimal text without rounding; throws before callers change any record. */
    fun validate(raw: String, isMileage: Boolean): String {
        val name = if (isMileage) "mileage_km" else "fuel_l"
        val value = raw.trim().toBigDecimalOrNull()
            ?: throw IllegalArgumentException("$name は有限の数値で指定してください。")
        val max = if (isMileage) maxMileage else maxFuel
        require(value >= BigDecimal.ZERO && (isMileage || value > BigDecimal.ZERO) && value <= max) {
            if (isMileage) "走行距離は0以上1000000000000以下で指定してください。"
            else "給油量は0より大きく1000000以下で指定してください。"
        }
        // Existing records use Double. Reject values which would silently become zero on save.
        val double = value.toDouble()
        require(double.isFinite() && (value.signum() == 0 || double != 0.0)) {
            "$name は保存できる数値の範囲で指定してください。"
        }
        // Prevent huge allocations from zero with an extreme exponent.
        if (value.signum() == 0) return "0"
        require(value.scale() in -1000..1000) { "$name の小数桁が多すぎます。" }
        return value.toPlainString()
    }
}

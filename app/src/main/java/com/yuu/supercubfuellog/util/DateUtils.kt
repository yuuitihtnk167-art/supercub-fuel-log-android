package com.yuu.supercubfuellog.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {
    private val dateFormatter = DateTimeFormatter.ISO_DATE
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

    fun toYearMonth(date: String): String? {
        return runCatching {
            val parsed = LocalDate.parse(date, dateFormatter)
            "%04d-%02d".format(parsed.year, parsed.monthValue)
        }.getOrNull()
    }

    fun formatDateTime(millis: Long?): String? {
        if (millis == null) return null
        return runCatching {
            val instant = Instant.ofEpochMilli(millis)
            dateTimeFormatter.withZone(ZoneId.systemDefault()).format(instant)
        }.getOrNull()
    }
}

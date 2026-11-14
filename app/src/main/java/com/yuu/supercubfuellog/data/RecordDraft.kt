package com.yuu.supercubfuellog.data

import java.time.LocalDate
import java.util.UUID

data class RecordDraft(
    val id: String = UUID.randomUUID().toString(),
    val date: String = LocalDate.now().toString(),
    val mileage: String = "",
    val fuel: String = "",
    val isEditing: Boolean = false
)

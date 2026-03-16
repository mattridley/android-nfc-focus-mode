package com.example.nfcappblocker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "enrolled_tags")
data class EnrolledTag(
    @PrimaryKey val tagId: String,
    val enrollmentDate: Long = System.currentTimeMillis()
)

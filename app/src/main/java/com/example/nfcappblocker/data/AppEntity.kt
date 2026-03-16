package com.example.nfcappblocker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whitelisted_apps")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val isWhitelisted: Boolean
)

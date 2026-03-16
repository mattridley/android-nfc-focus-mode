package com.example.nfcappblocker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM whitelisted_apps")
    fun getAllWhitelistedApps(): Flow<List<AppEntity>>

    @Query("SELECT EXISTS(SELECT * FROM whitelisted_apps WHERE packageName = :packageName AND isWhitelisted = 1)")
    suspend fun isAppWhitelisted(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppEntity)

    @Delete
    suspend fun deleteApp(app: AppEntity)

    // Enrolled Tags
    @Query("SELECT * FROM enrolled_tags")
    fun getAllEnrolledTags(): Flow<List<EnrolledTag>>

    @Query("SELECT EXISTS(SELECT * FROM enrolled_tags WHERE tagId = :tagId)")
    suspend fun isTagEnrolled(tagId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enrollTag(tag: EnrolledTag)

    @Delete
    suspend fun unrollTag(tag: EnrolledTag)
}

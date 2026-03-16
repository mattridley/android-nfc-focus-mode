package com.example.nfcappblocker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nfcappblocker.data.AppDao
import com.example.nfcappblocker.data.AppDatabase
import com.example.nfcappblocker.data.AppEntity
import com.example.nfcappblocker.data.EnrolledTag
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AppDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.appDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testAppWhitelisting() = runBlocking {
        val packageName = "com.test.app"
        dao.insertApp(AppEntity(packageName, true))
        assertTrue(dao.isAppWhitelisted(packageName))
        
        dao.insertApp(AppEntity(packageName, false))
        assertFalse(dao.isAppWhitelisted(packageName))
    }

    @Test
    fun testTagEnrollment() = runBlocking {
        val tagId = "ABC123DEF456"
        dao.enrollTag(EnrolledTag(tagId))
        assertTrue(dao.isTagEnrolled(tagId))
        
        dao.unrollTag(EnrolledTag(tagId))
        assertFalse(dao.isTagEnrolled(tagId))
    }
}

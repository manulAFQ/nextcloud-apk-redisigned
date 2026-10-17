package ru.manul.nextcloudgallery.data.local.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.manul.nextcloudgallery.core.model.AutoUploadPolicy
import ru.manul.nextcloudgallery.core.model.LocalMediaCandidate
import ru.manul.nextcloudgallery.core.model.LocalMediaKind
import ru.manul.nextcloudgallery.core.result.AppResult

@RunWith(AndroidJUnit4::class)
class RoomAutoUploadStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "auto-upload-test.db"
    private var database: GalleryDatabase? = null

    @Before fun prepare() { context.deleteDatabase(databaseName) }
    @After fun cleanUp() { database?.close(); context.deleteDatabase(databaseName) }

    @Test
    fun policyAndClaimSurviveRestartAndDuplicateIsRejected() = runBlocking {
        val first = GalleryDatabase.create(context, databaseName).also { database = it }
        first.accountDao().upsert(AccountEntity("account", "http://10.0.2.2:8080/", "user", null, true, 1))
        val store = RoomAutoUploadStore(first.autoUploadDao())
        val policy = AutoUploadPolicy(accountId = "account", enabled = true, deviceFolder = "Phone")
        store.save(policy)
        assertEquals(true, (store.claim("account", candidate(), 1) as AppResult.Success).value)
        assertEquals(false, (store.claim("account", candidate(), 2) as AppResult.Success).value)
        first.close()
        database = null

        val reopened = GalleryDatabase.create(context, databaseName).also { database = it }
        val restored = RoomAutoUploadStore(reopened.autoUploadDao())

        assertEquals(policy, (restored.get("account") as AppResult.Success).value)
        assertEquals(false, (restored.claim("account", candidate(), 3) as AppResult.Success).value)
    }

    private fun candidate() = LocalMediaCandidate(
        localKey = "external:1:10:20",
        contentUri = "content://media/external/images/media/1",
        displayName = "photo.jpg",
        relativePath = "DCIM/Camera/",
        kind = LocalMediaKind.IMAGE,
        mimeType = "image/jpeg",
        sizeBytes = 10,
        capturedAtEpochMs = 1,
        modifiedAtEpochSeconds = 20,
    )
}

package ru.manul.nextcloudgallery.data.local.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.model.UploadState
import ru.manul.nextcloudgallery.core.result.AppResult

@RunWith(AndroidJUnit4::class)
class RoomUploadQueueStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "upload-queue-test.db"
    private var database: GalleryDatabase? = null

    @Before
    fun prepare() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanUp() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun queuedJobSurvivesRestartAndInterruptedRunIsRecovered() = runBlocking {
        val first = GalleryDatabase.create(context, databaseName).also { database = it }
        first.accountDao().upsert(
            AccountEntity(
                accountId = "account",
                serverAddress = "http://10.0.2.2:8080/",
                username = "user",
                displayName = null,
                active = true,
                updatedAtEpochMs = 1,
            ),
        )
        val firstStore = RoomUploadQueueStore(first.uploadJobDao())
        assertTrue(firstStore.create(job()) is AppResult.Success)
        assertEquals(true, (firstStore.markRunning("job", 2) as AppResult.Success).value)
        assertEquals(
            true,
            (firstStore.resolveDestination("job", "/Photos/photo (1).jpg", 3) as AppResult.Success).value,
        )
        assertTrue(firstStore.create(job(id = "recent-job")) is AppResult.Success)
        assertEquals(
            true,
            (firstStore.markRunning("recent-job", 10L * 60 * 1_000 + 2) as AppResult.Success).value,
        )
        first.close()
        database = null

        val reopened = GalleryDatabase.create(context, databaseName).also { database = it }
        val reopenedStore = RoomUploadQueueStore(reopened.uploadJobDao())
        val running = (reopenedStore.get("job") as AppResult.Success).value!!
        assertEquals(UploadState.RUNNING, running.state)
        assertEquals(1, running.attempt)
        assertEquals("/Photos/photo (1).jpg", running.remotePath)
        assertTrue(running.destinationResolved)

        reopenedStore.recoverInterrupted("account", 10L * 60 * 1_000 + 3)
        val recovered = (reopenedStore.get("job") as AppResult.Success).value!!
        assertEquals(UploadState.QUEUED, recovered.state)
        assertEquals(0L, recovered.transferredBytes)
        assertEquals("/Photos/photo (1).jpg", recovered.remotePath)
        assertTrue(recovered.destinationResolved)

        val recent = (reopenedStore.get("recent-job") as AppResult.Success).value!!
        assertEquals(UploadState.RUNNING, recent.state)
    }

    private fun job(id: String = "job") = UploadJob(
        id = id,
        accountId = "account",
        localUri = "content://media/photo",
        remotePath = "/Photos/photo.jpg",
        sizeBytes = 100,
        transferredBytes = 0,
        state = UploadState.QUEUED,
        attempt = 0,
        failureMessage = null,
        nextAttemptEpochMs = null,
        destinationResolved = false,
        resultEtag = null,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )
}

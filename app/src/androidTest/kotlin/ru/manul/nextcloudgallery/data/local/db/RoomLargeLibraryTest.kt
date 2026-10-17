package ru.manul.nextcloudgallery.data.local.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.MediaKind

@RunWith(AndroidJUnit4::class)
class RoomLargeLibraryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "large-library-test.db"
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
    fun fiftyThousandItemsRemainPageableAfterProcessStyleRestart() = runBlocking {
        withContext(Dispatchers.IO) {
            val first = GalleryDatabase.create(context, databaseName).also { database = it }
            first.accountDao().upsert(account())
            RoomMediaIndex(first).replaceFolder(
                accountId = ACCOUNT_ID,
                remotePath = REMOTE_PATH,
                directoryEtag = "large-v1",
                syncGeneration = 1,
                syncedAtEpochMs = ITEM_COUNT.toLong(),
                items = List(ITEM_COUNT, ::media),
            )
            first.close()
            database = null

            val reopened = GalleryDatabase.create(context, databaseName).also { database = it }
            val lastPage = RoomMediaIndex(reopened).page(
                accountId = ACCOUNT_ID,
                remotePath = REMOTE_PATH,
                limit = 50,
                offset = ITEM_COUNT - 50,
            )

            assertEquals(50, lastPage.size)
            assertEquals("large-v1", RoomMediaIndex(reopened).cursor(ACCOUNT_ID, REMOTE_PATH)?.directoryEtag)
        }
    }

    private fun account() = AccountEntity(
        accountId = ACCOUNT_ID,
        serverAddress = "http://10.0.2.2:8080/",
        username = "ordinary-user",
        displayName = null,
        active = true,
        updatedAtEpochMs = 1,
    )

    private fun media(index: Int) = MediaItem(
        remotePath = "$REMOTE_PATH/photo-$index.jpg",
        displayName = "photo-$index.jpg",
        mimeType = "image/jpeg",
        kind = MediaKind.IMAGE,
        sizeBytes = index.toLong(),
        etag = "etag-$index",
        capturedAt = null,
        modifiedAt = Instant.ofEpochMilli(index.toLong()),
    )

    private companion object {
        const val ACCOUNT_ID = "large-account"
        const val REMOTE_PATH = "/Photos"
        const val ITEM_COUNT = 50_000
    }
}

package ru.manul.nextcloudgallery.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomMediaIndexTest {
    private lateinit var database: GalleryDatabase
    private lateinit var index: RoomMediaIndex

    @Before
    fun prepare() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java).build()
        index = RoomMediaIndex(database)
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun replacesFolderAtomicallyAndRemovesMissingRows() = runBlocking {
        withContext(Dispatchers.IO) {
            database.accountDao().upsert(
                AccountEntity(
                    accountId = "account-1",
                    serverAddress = "http://10.0.2.2:8080/",
                    username = "user",
                    displayName = null,
                    active = true,
                    updatedAtEpochMs = 1,
                ),
            )
            index.replaceFolder(
                accountId = "account-1",
                remotePath = "/Photos",
                directoryEtag = "folder-v1",
                syncGeneration = 1,
                syncedAtEpochMs = 100,
                items = listOf(media("old.jpg", 10), media("keep.jpg", 20)),
            )
            database.accountDao().upsert(
                database.accountDao().get("account-1")!!.copy(displayName = "Updated user"),
            )
            assertEquals(2, index.page("account-1", "/Photos", 50, 0).size)

            index.replaceFolder(
                accountId = "account-1",
                remotePath = "/Photos",
                directoryEtag = "folder-v2",
                syncGeneration = 2,
                syncedAtEpochMs = 200,
                items = listOf(media("keep.jpg", 30)),
            )

            val page = index.page("account-1", "/Photos", limit = 50, offset = 0)
            assertEquals(listOf("keep.jpg"), page.map(MediaItem::displayName))
            assertEquals("folder-v2", index.cursor("account-1", "/Photos")?.directoryEtag)
        }
    }

    private fun media(name: String, epochMs: Long) = MediaItem(
        remotePath = "/Photos/$name",
        displayName = name,
        mimeType = "image/jpeg",
        kind = MediaKind.IMAGE,
        sizeBytes = 10,
        etag = "etag-$name-$epochMs",
        capturedAt = null,
        modifiedAt = Instant.ofEpochMilli(epochMs),
    )
}

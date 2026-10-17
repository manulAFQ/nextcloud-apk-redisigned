package ru.manul.nextcloudgallery.data.local.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.MediaKind
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class GalleryDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "gallery-migration-test.db"
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
    fun migratesEmptyPrototypeFromVersion1ToCurrentVersion() {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { legacy ->
            legacy.version = 1
        }

        val migrated = GalleryDatabase.create(context, databaseName).also { database = it }
        val sqlite = migrated.openHelper.writableDatabase

        assertEquals(4, sqlite.version)
        listOf("accounts", "media", "sync_cursors", "upload_jobs", "auto_upload_policies", "auto_upload_items").forEach { table ->
            sqlite.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(table),
            ).use { cursor ->
                assertTrue("Missing table: $table", cursor.moveToFirst())
            }
        }
        sqlite.query("PRAGMA table_info(upload_jobs)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val columns = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
            assertTrue("Missing resolved destination column", "destination_resolved" in columns)
            assertTrue("Missing upload result ETag column", "result_etag" in columns)
        }
    }

    @Test
    fun cachedIndexSurvivesDatabaseRestart() = runBlocking {
        withContext(Dispatchers.IO) {
            val first = GalleryDatabase.create(context, databaseName).also { database = it }
            first.accountDao().upsert(
                AccountEntity(
                    accountId = "account-1",
                    serverAddress = "http://10.0.2.2:8080/",
                    username = "user",
                    displayName = null,
                    active = true,
                    updatedAtEpochMs = 1,
                ),
            )
            RoomMediaIndex(first).replaceFolder(
                accountId = "account-1",
                remotePath = "/Photos",
                directoryEtag = "etag-folder",
                syncGeneration = 1,
                syncedAtEpochMs = 10,
                items = listOf(
                    MediaItem(
                        remotePath = "/Photos/photo.jpg",
                        displayName = "photo.jpg",
                        mimeType = "image/jpeg",
                        kind = MediaKind.IMAGE,
                        sizeBytes = 100,
                        etag = "etag-photo",
                        capturedAt = null,
                        modifiedAt = Instant.ofEpochMilli(10),
                    ),
                ),
            )
            first.close()
            database = null

            val reopened = GalleryDatabase.create(context, databaseName).also { database = it }
            val cached = RoomMediaIndex(reopened).page("account-1", "/Photos", 50, 0)

            assertEquals(listOf("photo.jpg"), cached.map(MediaItem::displayName))
        }
    }

    @Test
    fun migrationFromVersion3PreservesExistingUploadQueue() = runBlocking {
        withContext(Dispatchers.IO) {
            val version4 = GalleryDatabase.create(context, databaseName).also { database = it }
            version4.accountDao().upsert(
                AccountEntity(
                    accountId = "account-1",
                    serverAddress = "http://10.0.2.2:8080/",
                    username = "user",
                    displayName = null,
                    active = true,
                    updatedAtEpochMs = 1,
                ),
            )
            version4.uploadJobDao().upsert(
                UploadJobEntity(
                    jobId = "preserved-job",
                    accountId = "account-1",
                    localUri = "content://media/photo",
                    remotePath = "/Photos/photo (1).jpg",
                    sizeBytes = 100,
                    transferredBytes = 40,
                    state = "QUEUED",
                    attempt = 2,
                    failureMessage = "server_unavailable",
                    nextAttemptEpochMs = 500,
                    destinationResolved = true,
                    resultEtag = null,
                    createdAtEpochMs = 10,
                    updatedAtEpochMs = 20,
                ),
            )
            version4.close()
            database = null

            context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { legacy ->
                legacy.execSQL("DROP TABLE auto_upload_items")
                legacy.execSQL("DROP TABLE auto_upload_policies")
                legacy.version = 3
            }

            val migrated = GalleryDatabase.create(context, databaseName).also { database = it }
            val preserved = migrated.uploadJobDao().get("preserved-job")

            assertEquals("/Photos/photo (1).jpg", preserved?.remotePath)
            assertEquals(40L, preserved?.transferredBytes)
            assertEquals(true, preserved?.destinationResolved)
            assertEquals(4, migrated.openHelper.writableDatabase.version)
        }
    }
}

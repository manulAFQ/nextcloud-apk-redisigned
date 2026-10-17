package ru.manul.nextcloudgallery.data.local.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSecretBoundaryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "secret-boundary-test.db"
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
    fun roomSchemaHasNoCredentialColumns() {
        val sqlite = GalleryDatabase.create(context, databaseName)
            .also { database = it }
            .openHelper
            .writableDatabase
        val forbiddenFragments = listOf("password", "passwd", "credential", "secret", "token")

        sqlite.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
        ).use { tables ->
            val tableNameIndex = tables.getColumnIndexOrThrow("name")
            while (tables.moveToNext()) {
                val tableName = tables.getString(tableNameIndex)
                sqlite.query("PRAGMA table_info(`$tableName`)").use { columns ->
                    val columnNameIndex = columns.getColumnIndexOrThrow("name")
                    while (columns.moveToNext()) {
                        val columnName = columns.getString(columnNameIndex).lowercase()
                        assertFalse(
                            "Credential-like Room column: $tableName.$columnName",
                            forbiddenFragments.any { fragment -> columnName.contains(fragment) },
                        )
                    }
                }
            }
        }
    }
}

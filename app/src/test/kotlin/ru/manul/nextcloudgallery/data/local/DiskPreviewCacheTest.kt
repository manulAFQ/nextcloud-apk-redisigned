package ru.manul.nextcloudgallery.data.local

import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

class DiskPreviewCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `preview survives cache lookup`() = runTest {
        val cache = DiskPreviewCache(temporaryFolder.newFolder("previews"), maxCacheBytes = 100)

        val stored = cache.put("key", bytes("preview"), maxEntryBytes = 20)
        val found = cache.get("key")

        assertTrueSuccess(stored)
        val file = (found as AppResult.Success).value
        assertNotNull(file)
        assertEquals("preview", file!!.readText())
    }

    @Test
    fun `entry larger than limit is rejected and temporary file removed`() = runTest {
        val directory = temporaryFolder.newFolder("previews")
        val cache = DiskPreviewCache(directory, maxCacheBytes = 100)

        val result = cache.put("key", bytes("too-large"), maxEntryBytes = 4)

        assertEquals(AppFailure.ResponseTooLarge, (result as AppResult.Failure).error)
        assertFalse(directory.listFiles().orEmpty().any { it.extension == "preview" })
    }

    @Test
    fun `least recently used entries are evicted`() = runTest {
        var now = 1_700_000_000_000L
        val cache = DiskPreviewCache(
            directory = temporaryFolder.newFolder("previews"),
            maxCacheBytes = 8,
            clock = { now++ },
        )

        assertTrueSuccess(cache.put("old", bytes("12345"), maxEntryBytes = 8))
        assertTrueSuccess(cache.put("new", bytes("67890"), maxEntryBytes = 8))

        assertNull((cache.get("old") as AppResult.Success).value)
        assertNotNull((cache.get("new") as AppResult.Success).value)
    }

    private fun bytes(value: String) = ByteArrayInputStream(value.toByteArray())

    private fun assertTrueSuccess(result: AppResult<*>) {
        assertTrue(result is AppResult.Success)
    }
}

package ru.manul.nextcloudgallery.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {
    @Test
    fun `adds http scheme and trailing slash`() {
        val address = ServerAddress.parse("10.0.2.2:8080").getOrThrow()

        assertEquals("http://10.0.2.2:8080/", address.value)
        assertEquals("http://10.0.2.2:8080/status.php", address.statusEndpoint)
    }

    @Test
    fun `preserves a nextcloud subpath`() {
        val address = ServerAddress.parse("https://cloud.example.test/nextcloud").getOrThrow()

        assertEquals("https://cloud.example.test/nextcloud/", address.value)
    }

    @Test
    fun `rejects credentials in address`() {
        val result = ServerAddress.parse("http://user:secret@10.0.2.2:8080")

        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects unsupported schemes`() {
        val result = ServerAddress.parse("file:///etc/passwd")

        assertTrue(result.isFailure)
    }
}

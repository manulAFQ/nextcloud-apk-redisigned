package ru.manul.nextcloudgallery.data.remote

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LimitedResponseReaderTest {
    @Test
    fun `reads a response shorter than its limit without requiring extra bytes`() {
        assertEquals("short", Buffer().writeUtf8("short").readUtf8Within(64))
    }

    @Test
    fun `accepts a response exactly at the limit`() {
        assertEquals("12345", Buffer().writeUtf8("12345").readUtf8Within(5))
    }

    @Test
    fun `rejects a response over the limit`() {
        assertNull(Buffer().writeUtf8("123456").readUtf8Within(5))
    }
}

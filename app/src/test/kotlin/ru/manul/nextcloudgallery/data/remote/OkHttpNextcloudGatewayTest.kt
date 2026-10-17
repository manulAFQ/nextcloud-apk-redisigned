package ru.manul.nextcloudgallery.data.remote

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

class OkHttpNextcloudGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: OkHttpNextcloudGateway

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = OkHttpNextcloudGateway()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `reads valid Nextcloud status`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .body(VALID_STATUS)
                .build(),
        )

        val result = gateway.probe(serverAddress())

        assertTrue(result is AppResult.Success)
        val info = (result as AppResult.Success).value
        assertEquals("35.0.1", info.version)
        assertEquals("Nextcloud", info.productName)
        assertEquals("/status.php", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `does not follow redirect`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(302)
                .addHeader("Location", "http://example.test/status.php")
                .build(),
        )

        val result = gateway.probe(serverAddress())

        assertFailure<AppFailure.UnsafeRedirect>(result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `rejects non-json response`() = runTest {
        server.enqueue(MockResponse.Builder().body("<html>not nextcloud</html>").build())

        val result = gateway.probe(serverAddress())

        assertFailure<AppFailure.InvalidServerResponse>(result)
    }

    @Test
    fun `maps forbidden response`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).build())

        val result = gateway.probe(serverAddress())

        assertFailure<AppFailure.Forbidden>(result)
    }

    private fun serverAddress(): ServerAddress = ServerAddress.parse(server.url("/").toString()).getOrThrow()

    private inline fun <reified T : AppFailure> assertFailure(result: AppResult<*>) {
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is T)
    }

    private companion object {
        val VALID_STATUS = """
            {
              "installed": true,
              "maintenance": false,
              "needsDbUpgrade": false,
              "version": "35.0.1.2",
              "versionstring": "35.0.1",
              "productname": "Nextcloud",
              "extraFutureField": "ignored"
            }
        """.trimIndent()
    }
}

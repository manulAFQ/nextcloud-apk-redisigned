package ru.manul.nextcloudgallery.data.remote

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.ServerCapabilities
import ru.manul.nextcloudgallery.core.model.ServerInfo
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

class OcsNextcloudGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: OcsNextcloudGateway

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = OcsNextcloudGateway(statusGateway = FakeStatusGateway())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `maps capabilities without exposing wire models`() = runTest {
        server.enqueue(MockResponse.Builder().body(CAPABILITIES_JSON).build())

        val result = gateway.capabilities(session())

        assertTrue(result is AppResult.Success)
        val capabilities = (result as AppResult.Success).value
        assertTrue(capabilities.chunkedUpload)
        assertTrue(capabilities.previews)
        assertTrue(capabilities.fileSharing)
        val request = server.takeRequest()
        assertEquals("/ocs/v1.php/cloud/capabilities", request.url.encodedPath)
        assertEquals("json", request.url.queryParameter("format"))
        assertEquals("true", request.headers["OCS-APIRequest"])
    }

    @Test
    fun `maps user profile and quota`() = runTest {
        server.enqueue(MockResponse.Builder().body(PROFILE_JSON).build())

        val result = gateway.profile(session())

        assertTrue(result is AppResult.Success)
        val profile = (result as AppResult.Success).value
        assertEquals("testuser", profile.userId)
        assertEquals("Ярослав", profile.displayName)
        assertEquals(1_000L, profile.quota?.totalBytes)
        assertEquals(250L, profile.quota?.usedBytes)
    }

    @Test
    fun `maps unauthorized without leaking credential`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())

        val result = gateway.capabilities(session())

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppFailure.Unauthorized)
        assertFalse(result.toString().contains(APP_PASSWORD))
    }

    private fun session(): UserSession = UserSession(
        account = Account(
            id = "account-id",
            serverAddress = ServerAddress.parse(server.url("/").toString()).getOrThrow(),
            username = "testuser",
            displayName = null,
        ),
        appPassword = AppPassword.create(APP_PASSWORD).getOrThrow(),
    )

    private class FakeStatusGateway : NextcloudGateway {
        override suspend fun probe(address: ServerAddress): AppResult<ServerInfo> =
            AppResult.Success(ServerInfo(true, false, false, "35", "Nextcloud"))

        override suspend fun capabilities(session: UserSession): AppResult<ServerCapabilities> =
            error("Not used")

        override suspend fun listMedia(
            session: UserSession,
            remotePath: String,
        ): AppResult<List<MediaItem>> = error("Not used")
    }

    private companion object {
        const val APP_PASSWORD = "never-print-this-app-password"

        val CAPABILITIES_JSON = """
            {
              "ocs": {
                "meta": {"status":"ok","statuscode":100,"message":"OK"},
                "data": {
                  "capabilities": {
                    "files": {"bigfilechunking":true},
                    "dav": {"chunking":"1.0"},
                    "files_sharing": {"api_enabled":true},
                    "unknown_future_capability": {"enabled":true}
                  }
                }
              }
            }
        """.trimIndent()

        val PROFILE_JSON = """
            {
              "ocs": {
                "meta": {"status":"ok","statuscode":200,"message":"OK"},
                "data": {
                  "id":"testuser",
                  "displayname":"Ярослав",
                  "email":null,
                  "quota":{"free":750,"used":250,"total":1000,"relative":25.0,"quota":1000}
                }
              }
            }
        """.trimIndent()
    }
}

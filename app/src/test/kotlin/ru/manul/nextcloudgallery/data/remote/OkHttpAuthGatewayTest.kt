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
import ru.manul.nextcloudgallery.core.model.LoginChallenge
import ru.manul.nextcloudgallery.core.model.LoginPollResult
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

class OkHttpAuthGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: OkHttpAuthGateway

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = OkHttpAuthGateway()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `starts login flow on expected endpoint`() = runTest {
        server.enqueue(MockResponse.Builder().body(startPayload()).build())

        val result = gateway.startLogin(serverAddress())

        assertTrue(result is AppResult.Success)
        val challenge = (result as AppResult.Success).value
        assertEquals("poll-secret", challenge.pollToken.reveal())
        assertFalse(challenge.toString().contains("poll-secret"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/index.php/login/v2", request.url.encodedPath)
    }

    @Test
    fun `rejects poll endpoint on another origin`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "poll":{"token":"poll-secret","endpoint":"http://evil.example/poll"},
                  "login":"${server.url("/login/v2/flow/id")}"
                }
                """.trimIndent(),
            ).build(),
        )

        val result = gateway.startLogin(serverAddress())

        assertFailure<AppFailure.UnsafeRedirect>(result)
    }

    @Test
    fun `404 while polling means authorization pending`() = runTest {
        server.enqueue(MockResponse.Builder().body(startPayload()).build())
        val challenge = successValue(gateway.startLogin(serverAddress()))
        server.enqueue(MockResponse.Builder().code(404).build())

        val result = gateway.pollLogin(challenge)

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).value is LoginPollResult.Pending)
    }

    @Test
    fun `authorized poll creates session without exposing app password`() = runTest {
        server.enqueue(MockResponse.Builder().body(startPayload()).build())
        val challenge = successValue(gateway.startLogin(serverAddress()))
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "server":"${server.url("/")}",
                  "loginName":"testuser",
                  "appPassword":"app-password-secret"
                }
                """.trimIndent(),
            ).build(),
        )

        val result = gateway.pollLogin(challenge)

        assertTrue(result is AppResult.Success)
        val authorized = (result as AppResult.Success).value as LoginPollResult.Authorized
        assertEquals("testuser", authorized.session.account.username)
        assertFalse(authorized.toString().contains("app-password-secret"))
    }

    @Test
    fun `rejects credentials returned for another server`() = runTest {
        server.enqueue(MockResponse.Builder().body(startPayload()).build())
        val challenge = successValue(gateway.startLogin(serverAddress()))
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "server":"http://evil.example/",
                  "loginName":"user",
                  "appPassword":"secret"
                }
                """.trimIndent(),
            ).build(),
        )

        val result = gateway.pollLogin(challenge)

        assertFailure<AppFailure.UnsafeRedirect>(result)
    }

    private fun startPayload(): String = """
        {
          "poll":{
            "token":"poll-secret",
            "endpoint":"${server.url("login/v2/poll")}"
          },
          "login":"${server.url("login/v2/flow/id")}"
        }
    """.trimIndent()

    private fun serverAddress() =
        ru.manul.nextcloudgallery.core.model.ServerAddress.parse(server.url("/").toString()).getOrThrow()

    private fun successValue(result: AppResult<LoginChallenge>): LoginChallenge =
        (result as AppResult.Success).value

    private inline fun <reified T : AppFailure> assertFailure(result: AppResult<*>) {
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is T)
    }
}

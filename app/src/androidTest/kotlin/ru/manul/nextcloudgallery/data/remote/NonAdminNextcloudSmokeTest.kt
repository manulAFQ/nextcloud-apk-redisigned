package ru.manul.nextcloudgallery.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

/**
 * Opt-in read-only integration test. Supply a disposable app password belonging to a
 * dedicated ordinary Nextcloud user through instrumentation arguments; never commit it.
 */
@RunWith(AndroidJUnit4::class)
class NonAdminNextcloudSmokeTest {
    @Test
    fun ordinaryUserCanReadStatusProfileCapabilitiesAndDavRoot() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val server = arguments.getString("ncServer")
        val username = arguments.getString("ncUsername")
        val password = arguments.getString("ncAppPassword")
        assumeTrue(
            "Skipped: ncServer, ncUsername and ncAppPassword were not supplied",
            !server.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank(),
        )

        val address = ServerAddress.parse(requireNotNull(server)).getOrThrow()
        val session = UserSession(
            account = Account(
                id = "integration-account",
                serverAddress = address,
                username = requireNotNull(username),
                displayName = null,
            ),
            appPassword = AppPassword.create(requireNotNull(password)).getOrThrow(),
        )

        assertTrue(OkHttpNextcloudGateway().probe(address) is AppResult.Success)
        assertTrue(OcsNextcloudGateway().profile(session) is AppResult.Success)
        assertTrue(OcsNextcloudGateway().capabilities(session) is AppResult.Success)
        assertTrue(WebDavMediaGateway().listMedia(session, "/") is AppResult.Success)
    }
}

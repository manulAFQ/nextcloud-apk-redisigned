package ru.manul.nextcloudgallery.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPasswordTest {
    @Test
    fun `session string never includes secret`() {
        val secret = "do-not-print-this-token"
        val password = AppPassword.create(secret).getOrThrow()
        val session = UserSession(
            account = Account(
                id = "account-1",
                serverAddress = ServerAddress.parse("http://10.0.2.2:8080").getOrThrow(),
                username = "user",
                displayName = null,
            ),
            appPassword = password,
        )

        assertFalse(session.toString().contains(secret))
        assertTrue(session.toString().contains("<redacted>"))
    }

    @Test
    fun `empty secret is rejected`() {
        assertTrue(AppPassword.create(" ").isFailure)
    }
}

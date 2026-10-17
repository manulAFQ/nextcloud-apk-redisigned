package ru.manul.nextcloudgallery.data.local

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

@RunWith(AndroidJUnit4::class)
class SecureStorageBoundaryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Before
    fun prepare() {
        preferences.edit().clear().commit()
    }

    @After
    fun cleanUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun appPasswordIsEncryptedAtRestAndBackupIsDisabled() = runBlocking {
        val passwordText = "instrumentation-secret-${System.nanoTime()}"
        val session = UserSession(
            account = Account(
                id = "security-test",
                serverAddress = ServerAddress.parse("http://10.0.2.2:8080").getOrThrow(),
                username = "ordinary-user",
                displayName = null,
            ),
            appPassword = AppPassword.create(passwordText).getOrThrow(),
        )
        val store = AndroidKeystoreSessionStore(context)

        assertTrue(store.save(session) is AppResult.Success)
        val loaded = (store.load() as AppResult.Success).value!!
        assertEquals("<redacted>", loaded.appPassword.toString())

        val preferencesFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/$PREFERENCES_NAME.xml",
        )
        assertTrue(preferencesFile.isFile)
        assertFalse(preferencesFile.readText().contains(passwordText))
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    private companion object {
        const val PREFERENCES_NAME = "encrypted_session"
    }
}

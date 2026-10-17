package ru.manul.nextcloudgallery.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

class AndroidKeystoreSessionStore(
    context: Context,
    private val json: Json = Json,
) : SessionStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun save(session: UserSession): AppResult<Unit> = protect {
        val payload = StoredSession(
            accountId = session.account.id,
            serverAddress = session.account.serverAddress.value,
            username = session.account.username,
            displayName = session.account.displayName,
            userId = session.account.userId,
            appPassword = session.appPassword.reveal(),
        )
        val encrypted = encrypt(json.encodeToString(StoredSession.serializer(), payload))
        check(preferences.edit().putString(SESSION_KEY, encrypted).commit()) {
            "Unable to persist encrypted session"
        }
    }

    override suspend fun load(): AppResult<UserSession?> = protect {
        val encrypted = preferences.getString(SESSION_KEY, null) ?: return@protect null
        val payload = json.decodeFromString(StoredSession.serializer(), decrypt(encrypted))
        val serverAddress = ServerAddress.parse(payload.serverAddress).getOrThrow()
        val password = AppPassword.create(payload.appPassword).getOrThrow()
        UserSession(
            account = Account(
                id = payload.accountId,
                serverAddress = serverAddress,
                username = payload.username,
                displayName = payload.displayName,
                userId = payload.userId ?: payload.username,
            ),
            appPassword = password,
        )
    }

    override suspend fun clear(): AppResult<Unit> = protect {
        check(preferences.edit().remove(SESSION_KEY).commit()) { "Unable to remove encrypted session" }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, encrypted)
            .joinToString(SEPARATOR) { bytes -> Base64.encodeToString(bytes, Base64.NO_WRAP) }
    }

    private fun decrypt(value: String): String {
        val parts = value.split(SEPARATOR, limit = 2)
        require(parts.size == 2) { "Invalid encrypted session" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private inline fun <T> protect(block: () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (_: Exception) {
        AppResult.Failure(AppFailure.SecureStorageUnavailable)
    }

    @Serializable
    private data class StoredSession(
        val accountId: String,
        val serverAddress: String,
        val username: String,
        val displayName: String?,
        val userId: String? = null,
        val appPassword: String,
    )

    private companion object {
        const val PREFERENCES_NAME = "encrypted_session"
        const val SESSION_KEY = "active_session"
        const val KEY_ALIAS = "nextcloud_gallery_session_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val SEPARATOR = "."
    }
}

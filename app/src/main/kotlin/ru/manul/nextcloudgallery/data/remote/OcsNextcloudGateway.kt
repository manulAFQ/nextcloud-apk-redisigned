package ru.manul.nextcloudgallery.data.remote

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.ServerCapabilities
import ru.manul.nextcloudgallery.core.model.ServerInfo
import ru.manul.nextcloudgallery.core.model.UserProfile
import ru.manul.nextcloudgallery.core.model.UserQuota
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import kotlin.coroutines.resume

class OcsNextcloudGateway(
    private val statusGateway: NextcloudGateway = OkHttpNextcloudGateway(),
    private val client: OkHttpClient = OkHttpNextcloudGateway.defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : NextcloudGateway {
    override suspend fun probe(address: ServerAddress): AppResult<ServerInfo> = statusGateway.probe(address)

    override suspend fun capabilities(session: UserSession): AppResult<ServerCapabilities> {
        val request = authenticatedGet(session, "ocs/v1.php/cloud/capabilities?format=json")
        return executeJson<OcsEnvelope<CapabilitiesData>, ServerCapabilities>(request) { envelope ->
            if (!envelope.ocs.meta.isSuccessful()) return@executeJson null
            val capabilities = envelope.ocs.data.capabilities
            ServerCapabilities(
                chunkedUpload = capabilities.files?.bigfilechunking == true ||
                    !capabilities.dav?.chunking.isNullOrBlank(),
                previews = capabilities.files != null,
                fileSharing = capabilities.filesSharing?.apiEnabled == true,
            )
        }
    }

    override suspend fun profile(session: UserSession): AppResult<UserProfile> {
        val request = authenticatedGet(session, "ocs/v2.php/cloud/user?format=json")
        return executeJson<OcsEnvelope<UserProfilePayload>, UserProfile>(request) { envelope ->
            if (!envelope.ocs.meta.isSuccessful()) return@executeJson null
            val profile = envelope.ocs.data
            if (profile.id.isBlank()) return@executeJson null
            UserProfile(
                userId = profile.id,
                displayName = profile.displayname.ifBlank { profile.id },
                email = profile.email?.takeIf(String::isNotBlank),
                quota = profile.quota?.toDomain(),
            )
        }
    }

    override suspend fun listMedia(
        session: UserSession,
        remotePath: String,
    ): AppResult<List<MediaItem>> = AppResult.Failure(AppFailure.UnsupportedServer)

    private fun authenticatedGet(session: UserSession, relativePath: String): Request = Request.Builder()
        .url("${session.account.serverAddress.value}$relativePath")
        .get()
        .header("Accept", "application/json")
        .header("OCS-APIRequest", "true")
        .header("Cache-Control", "no-store")
        .header("Authorization", Credentials.basic(session.account.username, session.appPassword.reveal()))
        .build()

    private suspend inline fun <reified Wire, Domain> executeJson(
        request: Request,
        crossinline mapper: (Wire) -> Domain?,
    ): AppResult<Domain> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(AppResult.Failure(AppFailure.ServerUnavailable))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!continuation.isActive) return
                        continuation.resume(mapResponse<Wire, Domain>(response, mapper))
                    }
                }
            },
        )
    }

    private inline fun <reified Wire, Domain> mapResponse(
        response: Response,
        mapper: (Wire) -> Domain?,
    ): AppResult<Domain> {
        if (response.isRedirect) return AppResult.Failure(AppFailure.UnsafeRedirect)
        if (!response.isSuccessful) return AppResult.Failure(httpFailure(response.code))
        val body = response.body ?: return AppResult.Failure(AppFailure.InvalidServerResponse)
        val text = body.source().readUtf8Within(MAX_OCS_RESPONSE_BYTES.toLong())
            ?: return AppResult.Failure(AppFailure.ResponseTooLarge)
        return try {
            val mapped = mapper(json.decodeFromString<Wire>(text))
            mapped?.let { value -> AppResult.Success(value) }
                ?: AppResult.Failure(AppFailure.InvalidServerResponse)
        } catch (_: SerializationException) {
            AppResult.Failure(AppFailure.InvalidServerResponse)
        } catch (_: IllegalArgumentException) {
            AppResult.Failure(AppFailure.InvalidServerResponse)
        }
    }

    private fun OcsMeta.isSuccessful(): Boolean = status == "ok" && statuscode in setOf(100, 200)

    private fun QuotaPayload.toDomain(): UserQuota? {
        val safeFree = free ?: return null
        val safeUsed = used ?: return null
        val safeTotal = total ?: return null
        return UserQuota(
            freeBytes = safeFree,
            usedBytes = safeUsed,
            totalBytes = safeTotal,
            relativePercent = relative ?: 0.0,
        )
    }

    private fun httpFailure(code: Int): AppFailure = when (code) {
        401 -> AppFailure.Unauthorized
        403 -> AppFailure.Forbidden
        507 -> AppFailure.InsufficientStorage
        else -> AppFailure.Http(code)
    }

    private companion object {
        const val MAX_OCS_RESPONSE_BYTES = 1024 * 1024
    }
}

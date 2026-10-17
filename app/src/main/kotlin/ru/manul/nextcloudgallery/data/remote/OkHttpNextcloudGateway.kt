package ru.manul.nextcloudgallery.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.ServerCapabilities
import ru.manul.nextcloudgallery.core.model.ServerInfo
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import kotlin.coroutines.resume

class OkHttpNextcloudGateway(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
) : NextcloudGateway {
    override suspend fun probe(address: ServerAddress): AppResult<ServerInfo> {
        val request = Request.Builder()
            .url(address.statusEndpoint)
            .get()
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .build()

        return when (val callResult = client.newCall(request).await()) {
            is CallResult.Completed -> callResult.response.use(::mapStatusResponse)
            is CallResult.Failed -> AppResult.Failure(mapNetworkFailure(callResult.error))
        }
    }

    override suspend fun capabilities(session: UserSession): AppResult<ServerCapabilities> =
        AppResult.Failure(AppFailure.UnsupportedServer)

    override suspend fun listMedia(
        session: UserSession,
        remotePath: String,
    ): AppResult<List<MediaItem>> = AppResult.Failure(AppFailure.UnsupportedServer)

    private fun mapStatusResponse(response: Response): AppResult<ServerInfo> {
        if (response.isRedirect) return AppResult.Failure(AppFailure.UnsafeRedirect)
        if (!response.isSuccessful) return AppResult.Failure(httpFailure(response.code))

        val body = response.body ?: return AppResult.Failure(AppFailure.InvalidServerResponse)
        val source = body.source()
        val text = source.readUtf8Within(MAX_STATUS_BYTES.toLong())
            ?: return AppResult.Failure(AppFailure.ResponseTooLarge)

        return try {
            val payload = json.decodeFromString<StatusPayload>(text)
            if (payload.productname.isBlank() || payload.version.isBlank()) {
                AppResult.Failure(AppFailure.InvalidServerResponse)
            } else {
                AppResult.Success(
                    ServerInfo(
                        installed = payload.installed,
                        maintenance = payload.maintenance,
                        needsDatabaseUpgrade = payload.needsDatabaseUpgrade,
                        version = payload.versionstring ?: payload.version,
                        productName = payload.productname,
                    ),
                )
            }
        } catch (_: SerializationException) {
            AppResult.Failure(AppFailure.InvalidServerResponse)
        } catch (_: IllegalArgumentException) {
            AppResult.Failure(AppFailure.InvalidServerResponse)
        }
    }

    private fun httpFailure(code: Int): AppFailure = when (code) {
        401 -> AppFailure.Unauthorized
        403 -> AppFailure.Forbidden
        507 -> AppFailure.InsufficientStorage
        else -> AppFailure.Http(code)
    }

    private fun mapNetworkFailure(error: IOException): AppFailure = when (error) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException -> AppFailure.ServerUnavailable
        else -> AppFailure.ServerUnavailable
    }

    private sealed interface CallResult {
        data class Completed(val response: Response) : CallResult
        data class Failed(val error: IOException) : CallResult
    }

    private suspend fun Call.await(): CallResult = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resume(CallResult.Failed(error))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(CallResult.Completed(response))
                    } else {
                        response.close()
                    }
                }
            },
        )
    }

    companion object {
        private const val MAX_STATUS_BYTES = 64 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }
    }
}

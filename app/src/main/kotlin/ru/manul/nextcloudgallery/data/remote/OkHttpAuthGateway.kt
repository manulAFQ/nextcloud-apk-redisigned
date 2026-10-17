package ru.manul.nextcloudgallery.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.LoginChallenge
import ru.manul.nextcloudgallery.core.model.LoginPollResult
import ru.manul.nextcloudgallery.core.model.LoginPollToken
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import kotlin.coroutines.resume

class OkHttpAuthGateway(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
) : AuthGateway {
    override suspend fun startLogin(serverAddress: ServerAddress): AppResult<LoginChallenge> {
        val request = Request.Builder()
            .url("${serverAddress.value}index.php/login/v2")
            .post(EMPTY_BODY)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .build()

        return execute(request) { response -> mapStartResponse(serverAddress, response) }
    }

    override suspend fun pollLogin(challenge: LoginChallenge): AppResult<LoginPollResult> {
        if (!isSameOrigin(challenge.serverAddress.value, challenge.pollEndpoint)) {
            return AppResult.Failure(AppFailure.UnsafeRedirect)
        }
        val body = FormBody.Builder()
            .add("token", challenge.pollToken.reveal())
            .build()
        val request = Request.Builder()
            .url(challenge.pollEndpoint)
            .post(body)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .build()

        return execute(request) { response -> mapPollResponse(challenge.serverAddress, response) }
    }

    override suspend fun revokeAppPassword(session: UserSession): AppResult<Unit> {
        val request = Request.Builder()
            .url("${session.account.serverAddress.value}ocs/v2.php/core/apppassword")
            .delete()
            .header("OCS-APIRequest", "true")
            .header("Authorization", Credentials.basic(session.account.username, session.appPassword.reveal()))
            .build()

        return execute(request) { response ->
            when {
                response.isRedirect -> AppResult.Failure(AppFailure.UnsafeRedirect)
                response.isSuccessful -> AppResult.Success(Unit)
                else -> AppResult.Failure(httpFailure(response.code))
            }
        }
    }

    private fun mapStartResponse(
        serverAddress: ServerAddress,
        response: Response,
    ): AppResult<LoginChallenge> {
        val responseText = responseText(response) ?: return responseFailure(response)
        return try {
            val payload = json.decodeFromString<LoginFlowStartPayload>(responseText)
            val token = LoginPollToken.create(payload.poll.token).getOrElse {
                return AppResult.Failure(AppFailure.InvalidServerResponse)
            }
            if (
                !isSameOrigin(serverAddress.value, payload.login) ||
                !isSameOrigin(serverAddress.value, payload.poll.endpoint)
            ) {
                AppResult.Failure(AppFailure.UnsafeRedirect)
            } else {
                AppResult.Success(
                    LoginChallenge(
                        serverAddress = serverAddress,
                        loginUrl = payload.login,
                        pollEndpoint = payload.poll.endpoint,
                        pollToken = token,
                    ),
                )
            }
        } catch (_: SerializationException) {
            AppResult.Failure(AppFailure.InvalidServerResponse)
        } catch (_: IllegalArgumentException) {
            AppResult.Failure(AppFailure.InvalidServerResponse)
        }
    }

    private fun mapPollResponse(
        requestedServer: ServerAddress,
        response: Response,
    ): AppResult<LoginPollResult> {
        if (response.code == 404) return AppResult.Success(LoginPollResult.Pending)
        val responseText = responseText(response) ?: return responseFailure(response)
        return try {
            val payload = json.decodeFromString<LoginFlowCredentialsPayload>(responseText)
            val returnedServer = ServerAddress.parse(payload.server).getOrElse {
                return AppResult.Failure(AppFailure.InvalidServerResponse)
            }
            val password = AppPassword.create(payload.appPassword).getOrElse {
                return AppResult.Failure(AppFailure.InvalidServerResponse)
            }
            if (!isSameOrigin(requestedServer.value, returnedServer.value) || payload.loginName.isBlank()) {
                AppResult.Failure(AppFailure.UnsafeRedirect)
            } else {
                val account = Account(
                    id = accountId(returnedServer, payload.loginName),
                    serverAddress = returnedServer,
                    username = payload.loginName,
                    displayName = null,
                )
                AppResult.Success(LoginPollResult.Authorized(UserSession(account, password)))
            }
        } catch (_: SerializationException) {
            AppResult.Failure(AppFailure.InvalidServerResponse)
        } catch (_: IllegalArgumentException) {
            AppResult.Failure(AppFailure.InvalidServerResponse)
        }
    }

    private suspend fun <T> execute(
        request: Request,
        mapper: (Response) -> AppResult<T>,
    ): AppResult<T> = when (val result = client.newCall(request).await()) {
        is CallResult.Completed -> result.response.use(mapper)
        is CallResult.Failed -> AppResult.Failure(mapNetworkFailure(result.error))
    }

    private fun responseText(response: Response): String? {
        if (response.isRedirect || !response.isSuccessful) return null
        val body = response.body ?: return null
        return body.source().readUtf8Within(MAX_AUTH_RESPONSE_BYTES.toLong())
    }

    private fun responseFailure(response: Response): AppResult.Failure = when {
        response.isRedirect -> AppResult.Failure(AppFailure.UnsafeRedirect)
        !response.isSuccessful -> AppResult.Failure(httpFailure(response.code))
        response.body == null -> AppResult.Failure(AppFailure.InvalidServerResponse)
        else -> AppResult.Failure(AppFailure.ResponseTooLarge)
    }

    private fun httpFailure(code: Int): AppFailure = when (code) {
        401 -> AppFailure.Unauthorized
        403 -> AppFailure.Forbidden
        else -> AppFailure.Http(code)
    }

    private fun mapNetworkFailure(error: IOException): AppFailure = when (error) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException -> AppFailure.ServerUnavailable
        else -> AppFailure.ServerUnavailable
    }

    private fun isSameOrigin(expected: String, actual: String): Boolean = runCatching {
        val expectedUri = URI(expected)
        val actualUri = URI(actual)
        expectedUri.scheme.equals(actualUri.scheme, ignoreCase = true) &&
            expectedUri.host.equals(actualUri.host, ignoreCase = true) &&
            effectivePort(expectedUri) == effectivePort(actualUri)
    }.getOrDefault(false)

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    private fun accountId(server: ServerAddress, loginName: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("${server.value}\n$loginName".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
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
        private const val MAX_AUTH_RESPONSE_BYTES = 64 * 1024
        private val EMPTY_BODY = FormBody.Builder().build()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

        fun defaultJson(): Json = Json { ignoreUnknownKeys = true }
    }
}

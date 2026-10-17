package ru.manul.nextcloudgallery.core.model

data class Account(
    val id: String,
    val serverAddress: ServerAddress,
    val username: String,
    val displayName: String?,
    /** Stable Nextcloud UID used in DAV paths. It may differ from the OIDC login name. */
    val userId: String = username,
)

data class UserSession(
    val account: Account,
    val appPassword: AppPassword,
)

class AppPassword private constructor(
    private val value: String,
) {
    internal fun reveal(): String = value

    override fun toString(): String = "<redacted>"

    companion object {
        fun create(value: String): Result<AppPassword> = runCatching {
            require(value.isNotBlank()) { "App password is empty" }
            AppPassword(value)
        }
    }
}

class LoginPollToken private constructor(
    private val value: String,
) {
    internal fun reveal(): String = value

    override fun toString(): String = "<redacted>"

    companion object {
        fun create(value: String): Result<LoginPollToken> = runCatching {
            require(value.isNotBlank()) { "Poll token is empty" }
            LoginPollToken(value)
        }
    }
}

data class LoginChallenge(
    val serverAddress: ServerAddress,
    val loginUrl: String,
    val pollEndpoint: String,
    val pollToken: LoginPollToken,
)

sealed interface LoginPollResult {
    data object Pending : LoginPollResult
    data class Authorized(val session: UserSession) : LoginPollResult
}

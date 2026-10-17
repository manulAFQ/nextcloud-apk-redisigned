package ru.manul.nextcloudgallery.data.remote

import kotlinx.serialization.Serializable

@Serializable
internal data class LoginFlowStartPayload(
    val poll: LoginFlowPollPayload,
    val login: String,
)

@Serializable
internal data class LoginFlowPollPayload(
    val token: String,
    val endpoint: String,
)

@Serializable
internal data class LoginFlowCredentialsPayload(
    val server: String,
    val loginName: String,
    val appPassword: String,
)

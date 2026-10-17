package ru.manul.nextcloudgallery.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class StatusPayload(
    val installed: Boolean,
    val maintenance: Boolean = false,
    @SerialName("needsDbUpgrade")
    val needsDatabaseUpgrade: Boolean = false,
    val version: String,
    val versionstring: String? = null,
    val productname: String,
)

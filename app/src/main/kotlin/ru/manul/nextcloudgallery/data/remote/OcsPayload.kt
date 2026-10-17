package ru.manul.nextcloudgallery.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OcsEnvelope<T>(val ocs: OcsBody<T>)

@Serializable
internal data class OcsBody<T>(
    val meta: OcsMeta,
    val data: T,
)

@Serializable
internal data class OcsMeta(
    val status: String,
    val statuscode: Int,
    val message: String = "",
)

@Serializable
internal data class CapabilitiesData(
    val capabilities: CapabilitiesPayload,
)

@Serializable
internal data class CapabilitiesPayload(
    val files: FilesCapabilities? = null,
    val dav: DavCapabilities? = null,
    @SerialName("files_sharing")
    val filesSharing: SharingCapabilities? = null,
)

@Serializable
internal data class FilesCapabilities(
    val bigfilechunking: Boolean = false,
)

@Serializable
internal data class DavCapabilities(
    val chunking: String? = null,
)

@Serializable
internal data class SharingCapabilities(
    @SerialName("api_enabled")
    val apiEnabled: Boolean = false,
)

@Serializable
internal data class UserProfilePayload(
    val id: String,
    val displayname: String,
    val email: String? = null,
    val quota: QuotaPayload? = null,
)

@Serializable
internal data class QuotaPayload(
    val free: Long? = null,
    val used: Long? = null,
    val total: Long? = null,
    val relative: Double? = null,
)

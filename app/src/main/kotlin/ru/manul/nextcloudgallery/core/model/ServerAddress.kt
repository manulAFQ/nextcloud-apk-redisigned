package ru.manul.nextcloudgallery.core.model

import java.net.URI

@JvmInline
value class ServerAddress private constructor(val value: String) {
    val statusEndpoint: String
        get() = "${value}status.php"

    companion object {
        fun parse(rawValue: String): Result<ServerAddress> = runCatching {
            val trimmed = rawValue.trim()
            require(trimmed.isNotEmpty()) { "Server address is empty" }

            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            val uri = URI(withScheme)
            require(uri.scheme == "http" || uri.scheme == "https") { "Only HTTP or HTTPS is supported" }
            require(!uri.host.isNullOrBlank()) { "Server host is missing" }
            require(uri.userInfo == null) { "Credentials must not be embedded in the URL" }
            require(uri.query == null && uri.fragment == null) { "Query and fragment are not allowed" }

            val normalizedPath = when {
                uri.path.isNullOrBlank() || uri.path == "/" -> "/"
                uri.path.endsWith("/") -> uri.path
                else -> "${uri.path}/"
            }
            val normalized = URI(uri.scheme, null, uri.host, uri.port, normalizedPath, null, null)
            ServerAddress(normalized.toASCIIString())
        }
    }
}

package ru.manul.nextcloudgallery.core.model

data class RemotePath private constructor(
    val value: String,
    val segments: List<String>,
) {
    fun child(name: String): Result<RemotePath> = parse((segments + name).joinToString("/"))

    companion object {
        fun parse(rawValue: String): Result<RemotePath> = runCatching {
            require('\u0000' !in rawValue) { "Remote path contains a null character" }
            require('\\' !in rawValue) { "Backslashes are not allowed" }
            val parts = rawValue.trim().trim('/').split('/').filter(String::isNotEmpty)
            require(parts.none { it == "." || it == ".." }) { "Relative path segments are not allowed" }
            require(parts.all { '/' !in it }) { "A path segment contains a separator" }
            val normalized = if (parts.isEmpty()) "/" else "/${parts.joinToString("/")}"
            RemotePath(normalized, parts)
        }
    }
}

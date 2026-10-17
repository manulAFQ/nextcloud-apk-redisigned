package ru.manul.nextcloudgallery.core.result

sealed interface AppFailure {
    data object NoNetwork : AppFailure
    data object ServerUnavailable : AppFailure
    data object Unauthorized : AppFailure
    data object Forbidden : AppFailure
    data object InsufficientStorage : AppFailure
    data object UnsupportedServer : AppFailure
    data object InvalidServerResponse : AppFailure
    data object UnsafeRedirect : AppFailure
    data object ResponseTooLarge : AppFailure
    data object SecureStorageUnavailable : AppFailure
    data object LoginTimedOut : AppFailure
    data object InvalidRemotePath : AppFailure
    data object LocalStorageUnavailable : AppFailure
    data object PreviewUnavailable : AppFailure
    data object InvalidPreviewRequest : AppFailure
    data object InvalidByteRange : AppFailure
    data object RangeNotSupported : AppFailure
    data object LocalContentUnavailable : AppFailure
    data object RemoteFileAlreadyExists : AppFailure
    data object UploadIntegrityMismatch : AppFailure
    data object MediaPermissionRequired : AppFailure
    data object WifiIdentityUnavailable : AppFailure
    data object AutoUploadConditionsNotMet : AppFailure
    data object UploadJobNotFound : AppFailure
    data class InvalidServerAddress(val reason: String) : AppFailure
    data class Http(val statusCode: Int) : AppFailure
    data class Unexpected(val safeMessage: String) : AppFailure
}

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppFailure) : AppResult<Nothing>
}

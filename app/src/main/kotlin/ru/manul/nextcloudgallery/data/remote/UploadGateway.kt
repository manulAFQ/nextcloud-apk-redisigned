package ru.manul.nextcloudgallery.data.remote

import java.io.InputStream
import ru.manul.nextcloudgallery.core.model.UploadReceipt
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

interface UploadGateway {
    suspend fun resolveUniqueRemotePath(
        session: UserSession,
        requestedRemotePath: String,
    ): AppResult<String>

    suspend fun upload(
        session: UserSession,
        uploadId: String,
        remotePath: String,
        sizeBytes: Long,
        recoverCompletedUpload: Boolean,
        mimeType: String?,
        source: () -> InputStream,
        onProgress: (Long) -> Unit,
    ): AppResult<UploadReceipt>

    /** Deletes only this user's temporary DAV upload collection. A missing collection is success. */
    suspend fun abort(session: UserSession, uploadId: String): AppResult<Unit>
}

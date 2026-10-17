package ru.manul.nextcloudgallery.data.remote

import ru.manul.nextcloudgallery.core.model.ByteRange
import ru.manul.nextcloudgallery.core.model.PreviewMode
import ru.manul.nextcloudgallery.core.model.RemoteMediaStream
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

interface MediaContentGateway {
    suspend fun openPreview(
        session: UserSession,
        remotePath: String,
        width: Int,
        height: Int,
        mode: PreviewMode,
        version: String?,
    ): AppResult<RemoteMediaStream>

    suspend fun openOriginal(
        session: UserSession,
        remotePath: String,
        range: ByteRange?,
    ): AppResult<RemoteMediaStream>
}

package ru.manul.nextcloudgallery.domain.repository

import ru.manul.nextcloudgallery.core.model.ByteRange
import ru.manul.nextcloudgallery.core.model.CachedPreview
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.PreviewMode
import ru.manul.nextcloudgallery.core.model.RemoteMediaStream
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

interface MediaContentRepository {
    suspend fun preview(
        session: UserSession,
        item: MediaItem,
        width: Int,
        height: Int,
        mode: PreviewMode = PreviewMode.COVER,
    ): AppResult<CachedPreview>

    suspend fun openOriginal(
        session: UserSession,
        remotePath: String,
        range: ByteRange? = null,
    ): AppResult<RemoteMediaStream>
}

package ru.manul.nextcloudgallery.domain.repository

import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

interface MediaRepository {
    suspend fun listFolder(session: UserSession, remotePath: String): AppResult<List<MediaItem>>
}

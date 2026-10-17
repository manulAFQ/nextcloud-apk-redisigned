package ru.manul.nextcloudgallery.data.repository

import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.remote.NextcloudGateway
import ru.manul.nextcloudgallery.domain.repository.MediaRepository

class DefaultMediaRepository(
    private val webDavGateway: NextcloudGateway,
) : MediaRepository {
    override suspend fun listFolder(
        session: UserSession,
        remotePath: String,
    ): AppResult<List<MediaItem>> = webDavGateway.listMedia(session, remotePath)
}

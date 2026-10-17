package ru.manul.nextcloudgallery.data.remote

import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.MediaFolderChange
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.ServerCapabilities
import ru.manul.nextcloudgallery.core.model.ServerInfo
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.model.UserProfile
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

interface NextcloudGateway {
    suspend fun probe(address: ServerAddress): AppResult<ServerInfo>
    suspend fun capabilities(session: UserSession): AppResult<ServerCapabilities>
    suspend fun profile(session: UserSession): AppResult<UserProfile> =
        AppResult.Failure(AppFailure.UnsupportedServer)
    suspend fun listMedia(session: UserSession, remotePath: String): AppResult<List<MediaItem>>

    suspend fun readMediaFolder(
        session: UserSession,
        remotePath: String,
        previousDirectoryEtag: String?,
    ): AppResult<MediaFolderChange> = when (val result = listMedia(session, remotePath)) {
        is AppResult.Success -> AppResult.Success(
            MediaFolderChange.Snapshot(directoryEtag = null, items = result.value),
        )
        is AppResult.Failure -> result
    }
}

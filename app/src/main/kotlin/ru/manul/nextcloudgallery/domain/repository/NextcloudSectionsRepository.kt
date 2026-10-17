package ru.manul.nextcloudgallery.domain.repository

import ru.manul.nextcloudgallery.core.model.ActivityEvent
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.RemoteAlbum
import ru.manul.nextcloudgallery.core.model.TrashItem
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

interface NextcloudSectionsRepository {
    suspend fun activities(session: UserSession, limit: Int = 50): AppResult<List<ActivityEvent>>
    suspend fun media(session: UserSession, limit: Int = 2_000, offset: Int = 0): AppResult<List<MediaItem>>
    suspend fun favorites(session: UserSession, limit: Int = 500, offset: Int = 0): AppResult<List<MediaItem>>
    suspend fun albums(session: UserSession): AppResult<List<RemoteAlbum>>
    suspend fun albumMedia(session: UserSession, remotePath: String): AppResult<List<MediaItem>>
    suspend fun createAlbum(session: UserSession, name: String): AppResult<RemoteAlbum>
    suspend fun addToAlbum(session: UserSession, album: RemoteAlbum, items: List<MediaItem>): AppResult<Unit>
    suspend fun deleteMedia(session: UserSession, items: List<MediaItem>): AppResult<Unit>
    suspend fun setFavorite(session: UserSession, item: MediaItem, favorite: Boolean): AppResult<Unit>
    suspend fun trash(session: UserSession): AppResult<List<TrashItem>>
    suspend fun restore(session: UserSession, item: TrashItem): AppResult<Unit>
    suspend fun deletePermanently(session: UserSession, item: TrashItem): AppResult<Unit>
}

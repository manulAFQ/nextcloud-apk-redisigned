package ru.manul.nextcloudgallery.data.local

import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

interface SessionStore {
    suspend fun save(session: UserSession): AppResult<Unit>
    suspend fun load(): AppResult<UserSession?>
    suspend fun clear(): AppResult<Unit>
}

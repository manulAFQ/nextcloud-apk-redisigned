package ru.manul.nextcloudgallery.domain.repository

import ru.manul.nextcloudgallery.core.model.ServerCapabilities
import ru.manul.nextcloudgallery.core.model.UserProfile
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

interface AccountRepository {
    suspend fun capabilities(session: UserSession): AppResult<ServerCapabilities>
    suspend fun profile(session: UserSession): AppResult<UserProfile>
}

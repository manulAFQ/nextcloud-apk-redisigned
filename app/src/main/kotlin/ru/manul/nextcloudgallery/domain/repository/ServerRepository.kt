package ru.manul.nextcloudgallery.domain.repository

import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.ServerInfo
import ru.manul.nextcloudgallery.core.result.AppResult

interface ServerRepository {
    suspend fun check(rawAddress: String): AppResult<ServerInfo>
}

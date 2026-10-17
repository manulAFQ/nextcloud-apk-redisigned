package ru.manul.nextcloudgallery.data.repository

import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.ServerInfo
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.remote.NextcloudGateway
import ru.manul.nextcloudgallery.domain.repository.ServerRepository

class DefaultServerRepository(
    private val gateway: NextcloudGateway,
) : ServerRepository {
    override suspend fun check(rawAddress: String): AppResult<ServerInfo> {
        val address = ServerAddress.parse(rawAddress).getOrElse { error ->
            return AppResult.Failure(
                AppFailure.InvalidServerAddress(error.message ?: "Invalid server address"),
            )
        }
        return gateway.probe(address)
    }
}

package ru.manul.nextcloudgallery.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.ServerCapabilities
import ru.manul.nextcloudgallery.core.model.ServerInfo
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.remote.NextcloudGateway

class DefaultServerRepositoryTest {
    @Test
    fun `invalid address never reaches gateway`() = runTest {
        val gateway = FakeGateway()
        val repository = DefaultServerRepository(gateway)

        val result = repository.check("file:///server")

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppFailure.InvalidServerAddress)
        assertEquals(0, gateway.probeCalls)
    }

    @Test
    fun `valid address is normalized before probe`() = runTest {
        val gateway = FakeGateway()
        val repository = DefaultServerRepository(gateway)

        val result = repository.check("10.0.2.2:8080")

        assertTrue(result is AppResult.Success)
        assertEquals("http://10.0.2.2:8080/", gateway.lastAddress?.value)
    }

    private class FakeGateway : NextcloudGateway {
        var probeCalls = 0
        var lastAddress: ServerAddress? = null

        override suspend fun probe(address: ServerAddress): AppResult<ServerInfo> {
            probeCalls += 1
            lastAddress = address
            return AppResult.Success(
                ServerInfo(
                    installed = true,
                    maintenance = false,
                    needsDatabaseUpgrade = false,
                    version = "35.0.1",
                    productName = "Nextcloud",
                ),
            )
        }

        override suspend fun capabilities(session: UserSession): AppResult<ServerCapabilities> =
            error("Not used by this test")

        override suspend fun listMedia(
            session: UserSession,
            remotePath: String,
        ): AppResult<List<MediaItem>> = error("Not used by this test")
    }
}

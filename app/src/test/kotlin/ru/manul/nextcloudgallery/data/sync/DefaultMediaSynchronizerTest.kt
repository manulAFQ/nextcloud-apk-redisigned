package ru.manul.nextcloudgallery.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.MediaFolderChange
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.MediaKind
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.ServerCapabilities
import ru.manul.nextcloudgallery.core.model.ServerInfo
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.remote.NextcloudGateway
import ru.manul.nextcloudgallery.domain.repository.AccountIndex
import ru.manul.nextcloudgallery.domain.repository.MediaIndex
import ru.manul.nextcloudgallery.domain.repository.SyncCursor
import ru.manul.nextcloudgallery.domain.sync.SyncOutcome
import java.time.Instant

class DefaultMediaSynchronizerTest {
    @Test
    fun `unchanged etag does not replace cached rows`() = runTest {
        val index = FakeMediaIndex(
            storedCursor = SyncCursor("account", "/Photos", "same-etag", 10, 4),
        )
        val gateway = FakeGateway(MediaFolderChange.Unchanged("same-etag"))
        val synchronizer = synchronizer(gateway, index)

        val result = synchronizer.sync(session(), "/Photos")

        assertTrue((result as AppResult.Success).value is SyncOutcome.Unchanged)
        assertEquals("same-etag", gateway.receivedPreviousEtag)
        assertEquals(0, index.replaceCalls)
        assertEquals(100L, index.markedAt)
    }

    @Test
    fun `changed snapshot advances generation and replaces cache`() = runTest {
        val index = FakeMediaIndex(
            storedCursor = SyncCursor("account", "/Photos", "old-etag", 10, 4),
        )
        val gateway = FakeGateway(MediaFolderChange.Snapshot("new-etag", listOf(media())))
        val synchronizer = synchronizer(gateway, index)

        val result = synchronizer.sync(session(), "/Photos")

        val updated = (result as AppResult.Success).value as SyncOutcome.Updated
        assertEquals(5L, updated.generation)
        assertEquals(1, updated.itemCount)
        assertEquals(1, index.replaceCalls)
        assertEquals(5L, index.replacedGeneration)
    }

    @Test
    fun `remote failure leaves cached snapshot untouched`() = runTest {
        val index = FakeMediaIndex()
        val gateway = FakeGateway(failure = AppFailure.ServerUnavailable)
        val synchronizer = synchronizer(gateway, index)

        val result = synchronizer.sync(session(), "/Photos")

        assertTrue(result is AppResult.Failure)
        assertEquals(0, index.replaceCalls)
    }

    @Test
    fun `parallel run for same folder is skipped`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gateway = FakeGateway(MediaFolderChange.Snapshot("etag", emptyList())).apply {
            beforeResult = {
                entered.complete(Unit)
                release.await()
            }
        }
        val synchronizer = synchronizer(gateway, FakeMediaIndex())

        val first = async { synchronizer.sync(session(), "/Photos") }
        entered.await()
        val second = synchronizer.sync(session(), "/Photos")
        release.complete(Unit)
        first.await()

        assertEquals(SyncOutcome.AlreadyRunning, (second as AppResult.Success).value)
        assertEquals(1, gateway.calls)
    }

    private fun synchronizer(gateway: FakeGateway, index: FakeMediaIndex) = DefaultMediaSynchronizer(
        gateway = gateway,
        accountIndex = object : AccountIndex {
            override suspend fun activate(account: Account, updatedAtEpochMs: Long) = Unit
        },
        mediaIndex = index,
        clock = { 100L },
    )

    private class FakeGateway(
        private val change: MediaFolderChange? = null,
        private val failure: AppFailure? = null,
    ) : NextcloudGateway {
        var receivedPreviousEtag: String? = null
        var calls: Int = 0
        var beforeResult: suspend () -> Unit = {}

        override suspend fun readMediaFolder(
            session: UserSession,
            remotePath: String,
            previousDirectoryEtag: String?,
        ): AppResult<MediaFolderChange> {
            calls += 1
            receivedPreviousEtag = previousDirectoryEtag
            beforeResult()
            return failure?.let { value -> AppResult.Failure(value) }
                ?: AppResult.Success(requireNotNull(change))
        }

        override suspend fun probe(address: ServerAddress): AppResult<ServerInfo> =
            AppResult.Failure(AppFailure.UnsupportedServer)

        override suspend fun capabilities(session: UserSession): AppResult<ServerCapabilities> =
            AppResult.Failure(AppFailure.UnsupportedServer)

        override suspend fun listMedia(
            session: UserSession,
            remotePath: String,
        ): AppResult<List<MediaItem>> = AppResult.Failure(AppFailure.UnsupportedServer)
    }

    private class FakeMediaIndex(
        var storedCursor: SyncCursor? = null,
    ) : MediaIndex {
        var replaceCalls = 0
        var replacedGeneration: Long? = null
        var markedAt: Long? = null

        override fun observeFolder(accountId: String, remotePath: String): Flow<List<MediaItem>> = flowOf()

        override suspend fun page(
            accountId: String,
            remotePath: String,
            limit: Int,
            offset: Int,
        ): List<MediaItem> = emptyList()

        override suspend fun cursor(accountId: String, remotePath: String): SyncCursor? = storedCursor

        override suspend fun markFolderChecked(
            accountId: String,
            remotePath: String,
            directoryEtag: String,
            checkedAtEpochMs: Long,
        ) {
            markedAt = checkedAtEpochMs
        }

        override suspend fun replaceFolder(
            accountId: String,
            remotePath: String,
            directoryEtag: String?,
            syncGeneration: Long,
            syncedAtEpochMs: Long,
            items: List<MediaItem>,
        ) {
            replaceCalls += 1
            replacedGeneration = syncGeneration
        }
    }

    private companion object {
        fun session() = UserSession(
            account = Account(
                id = "account",
                serverAddress = ServerAddress.parse("http://10.0.2.2:8080").getOrThrow(),
                username = "user",
                displayName = null,
            ),
            appPassword = AppPassword.create("secret").getOrThrow(),
        )

        fun media() = MediaItem(
            remotePath = "/Photos/photo.jpg",
            displayName = "photo.jpg",
            mimeType = "image/jpeg",
            kind = MediaKind.IMAGE,
            sizeBytes = 1,
            etag = "file-etag",
            capturedAt = null,
            modifiedAt = Instant.ofEpochMilli(1),
        )
    }
}

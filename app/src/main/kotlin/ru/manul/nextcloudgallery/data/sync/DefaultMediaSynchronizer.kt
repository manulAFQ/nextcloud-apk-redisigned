package ru.manul.nextcloudgallery.data.sync

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import ru.manul.nextcloudgallery.core.model.MediaFolderChange
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.remote.NextcloudGateway
import ru.manul.nextcloudgallery.domain.repository.AccountIndex
import ru.manul.nextcloudgallery.domain.repository.MediaIndex
import ru.manul.nextcloudgallery.domain.sync.MediaSynchronizer
import ru.manul.nextcloudgallery.domain.sync.SyncOutcome

class DefaultMediaSynchronizer(
    private val gateway: NextcloudGateway,
    private val accountIndex: AccountIndex,
    private val mediaIndex: MediaIndex,
    private val clock: () -> Long = System::currentTimeMillis,
    private val locks: SyncLockRegistry = SyncLockRegistry(),
) : MediaSynchronizer {
    override suspend fun sync(
        session: UserSession,
        remotePath: String,
        force: Boolean,
    ): AppResult<SyncOutcome> {
        val lock = locks.forFolder(session.account.id, remotePath)
        if (!lock.tryLock()) return AppResult.Success(SyncOutcome.AlreadyRunning)

        return try {
            syncLocked(session, remotePath, force)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppResult.Failure(AppFailure.LocalStorageUnavailable)
        } finally {
            lock.unlock()
        }
    }

    private suspend fun syncLocked(
        session: UserSession,
        remotePath: String,
        force: Boolean,
    ): AppResult<SyncOutcome> {
        val checkedAt = clock()
        accountIndex.activate(session.account, checkedAt)
        val cursor = mediaIndex.cursor(session.account.id, remotePath)
        val previousEtag = cursor?.directoryEtag.takeUnless { force }

        return when (val remote = gateway.readMediaFolder(session, remotePath, previousEtag)) {
            is AppResult.Failure -> remote
            is AppResult.Success -> when (val change = remote.value) {
                is MediaFolderChange.Unchanged -> {
                    mediaIndex.markFolderChecked(
                        accountId = session.account.id,
                        remotePath = remotePath,
                        directoryEtag = change.directoryEtag,
                        checkedAtEpochMs = checkedAt,
                    )
                    AppResult.Success(SyncOutcome.Unchanged(change.directoryEtag, checkedAt))
                }
                is MediaFolderChange.Snapshot -> {
                    val generation = cursor?.syncGeneration.nextGeneration()
                    mediaIndex.replaceFolder(
                        accountId = session.account.id,
                        remotePath = remotePath,
                        directoryEtag = change.directoryEtag,
                        syncGeneration = generation,
                        syncedAtEpochMs = checkedAt,
                        items = change.items,
                    )
                    AppResult.Success(
                        SyncOutcome.Updated(
                            itemCount = change.items.size,
                            generation = generation,
                            directoryEtag = change.directoryEtag,
                        ),
                    )
                }
            }
        }
    }

    private fun Long?.nextGeneration(): Long = when (this) {
        null, Long.MAX_VALUE -> 1L
        else -> this + 1L
    }
}

class SyncLockRegistry {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun forFolder(accountId: String, remotePath: String): Mutex =
        locks.getOrPut("$accountId\u0000$remotePath") { Mutex() }
}

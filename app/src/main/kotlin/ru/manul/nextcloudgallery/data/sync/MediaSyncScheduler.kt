package ru.manul.nextcloudgallery.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.domain.sync.SessionLifecycleObserver

interface SyncScheduler {
    fun schedulePeriodic(accountId: String, remotePath: String = DEFAULT_MEDIA_PATH)

    fun enqueueImmediate(
        accountId: String,
        remotePath: String = DEFAULT_MEDIA_PATH,
        force: Boolean = false,
    )

    fun cancelForAccount(accountId: String)

    companion object {
        const val DEFAULT_MEDIA_PATH = "/Photos"
    }
}

class MediaSyncScheduler(
    private val workManager: WorkManager,
) : SyncScheduler {
    override fun schedulePeriodic(accountId: String, remotePath: String) {
        val identity = identity(accountId, remotePath)
        val request = PeriodicWorkRequestBuilder<MediaSyncWorker>(
            1,
            TimeUnit.HOURS,
            15,
            TimeUnit.MINUTES,
        )
            .setInputData(input(accountId, remotePath, force = false))
            .setConstraints(periodicConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_ALL_SYNC)
            .addTag(accountTag(accountId))
            .build()
        workManager.enqueueUniquePeriodicWork(
            "periodic-media-sync-$identity",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun enqueueImmediate(accountId: String, remotePath: String, force: Boolean) {
        val identity = identity(accountId, remotePath)
        val request = OneTimeWorkRequestBuilder<MediaSyncWorker>()
            .setInputData(input(accountId, remotePath, force))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_ALL_SYNC)
            .addTag(accountTag(accountId))
            .build()
        workManager.enqueueUniqueWork(
            "immediate-media-sync-$identity",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancelForAccount(accountId: String) {
        workManager.cancelAllWorkByTag(accountTag(accountId))
    }

    private fun periodicConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    private fun input(accountId: String, remotePath: String, force: Boolean) = workDataOf(
        MediaSyncWorker.KEY_ACCOUNT_ID to accountId,
        MediaSyncWorker.KEY_REMOTE_PATH to remotePath,
        MediaSyncWorker.KEY_FORCE to force,
    )

    private fun identity(accountId: String, remotePath: String): String = sha256("$accountId\u0000$remotePath")

    private fun accountTag(accountId: String): String = "media-sync-account-${sha256(accountId)}"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val TAG_ALL_SYNC = "media-sync"
    }
}

class WorkSchedulingSessionObserver(
    private val scheduler: SyncScheduler,
) : SessionLifecycleObserver {
    override fun onSessionAvailable(session: UserSession) {
        scheduler.schedulePeriodic(session.account.id)
        scheduler.enqueueImmediate(session.account.id)
    }

    override fun onSessionRemoved(accountId: String) {
        scheduler.cancelForAccount(accountId)
    }
}

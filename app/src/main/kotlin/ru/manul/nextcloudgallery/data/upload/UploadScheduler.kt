package ru.manul.nextcloudgallery.data.upload

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

interface UploadScheduler {
    fun enqueue(jobId: String, accountId: String, initialDelayMillis: Long = 0, replace: Boolean = false)

    fun cancel(jobId: String)

    fun cancelForAccount(accountId: String)
}

class WorkManagerUploadScheduler(
    private val workManager: WorkManager,
) : UploadScheduler {
    override fun enqueue(jobId: String, accountId: String, initialDelayMillis: Long, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(
                workDataOf(
                    UploadWorker.KEY_JOB_ID to jobId,
                    UploadWorker.KEY_ACCOUNT_ID to accountId,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setInitialDelay(initialDelayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_ALL_UPLOADS)
            .addTag(accountTag(accountId))
            .build()
        workManager.enqueueUniqueWork(
            workName(jobId),
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(jobId: String) {
        workManager.cancelUniqueWork(workName(jobId))
    }

    override fun cancelForAccount(accountId: String) {
        workManager.cancelAllWorkByTag(accountTag(accountId))
    }

    private fun workName(jobId: String): String = "media-upload-${sha256(jobId)}"

    private fun accountTag(accountId: String): String = "media-upload-account-${sha256(accountId)}"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val TAG_ALL_UPLOADS = "media-upload"
        const val MIN_BACKOFF_SECONDS = 30L
    }
}

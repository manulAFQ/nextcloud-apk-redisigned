package ru.manul.nextcloudgallery.data.autoupload

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
import ru.manul.nextcloudgallery.core.model.AutoUploadPolicy

interface AutoUploadScheduler {
    fun schedule(policy: AutoUploadPolicy)
    fun runNow(policy: AutoUploadPolicy)
    fun cancel(accountId: String)
}

class WorkManagerAutoUploadScheduler(private val workManager: WorkManager) : AutoUploadScheduler {
    override fun schedule(policy: AutoUploadPolicy) {
        if (!policy.enabled) return cancel(policy.accountId)
        val request = PeriodicWorkRequestBuilder<AutoUploadWorker>(MIN_PERIOD_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(AutoUploadWorker.KEY_ACCOUNT_ID to policy.accountId))
            .setConstraints(policy.constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(accountTag(policy.accountId))
            .build()
        workManager.enqueueUniquePeriodicWork(
            periodicName(policy.accountId),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        runNow(policy)
    }

    override fun runNow(policy: AutoUploadPolicy) {
        if (!policy.enabled) return
        val request = OneTimeWorkRequestBuilder<AutoUploadWorker>()
            .setInputData(workDataOf(AutoUploadWorker.KEY_ACCOUNT_ID to policy.accountId))
            .setConstraints(policy.constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(accountTag(policy.accountId))
            .build()
        workManager.enqueueUniqueWork(immediateName(policy.accountId), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(accountId: String) {
        workManager.cancelAllWorkByTag(accountTag(accountId))
    }

    private fun AutoUploadPolicy.constraints() = Constraints.Builder()
        // WorkManager cannot express "Wi-Fi of any metering status"; the runner
        // performs the exact transport and SSID checks immediately before scanning.
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresCharging(requireCharging)
        .setRequiresBatteryNotLow(minimumBatteryPercent > 0)
        .setRequiresStorageNotLow(true)
        .build()

    private fun periodicName(accountId: String) = "auto-upload-periodic-${hash(accountId)}"
    private fun immediateName(accountId: String) = "auto-upload-now-${hash(accountId)}"
    private fun accountTag(accountId: String) = "auto-upload-account-${hash(accountId)}"
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MIN_PERIOD_MINUTES = 15L
        const val MIN_BACKOFF_SECONDS = 30L
    }
}

package ru.manul.nextcloudgallery.data.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf

interface UploadWorkerDependencies {
    val uploadWorkerRunner: UploadWorkerRunner
}

class UploadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return invalidInput()
        val accountId = inputData.getString(KEY_ACCOUNT_ID) ?: return invalidInput()
        val dependencies = applicationContext as? UploadWorkerDependencies
            ?: return Result.failure(workDataOf(KEY_ERROR to "missing_dependencies"))

        setForeground(foregroundInfo(jobId, transferredBytes = 0, totalBytes = 0))
        return when (
            val decision = dependencies.uploadWorkerRunner.run(jobId, accountId) { transferred, total ->
                setProgress(
                    workDataOf(
                        KEY_TRANSFERRED_BYTES to transferred,
                        KEY_TOTAL_BYTES to total,
                    ),
                )
                setForeground(foregroundInfo(jobId, transferred, total))
            }
        ) {
            is UploadRunDecision.Success -> Result.success(
                workDataOf(KEY_TRANSFERRED_BYTES to decision.transferredBytes),
            )
            is UploadRunDecision.Retry -> Result.retry()
            is UploadRunDecision.Failure -> Result.failure(
                workDataOf(KEY_ERROR to decision.failure::class.simpleName.orEmpty()),
            )
        }
    }

    private fun foregroundInfo(jobId: String, transferredBytes: Long, totalBytes: Long): ForegroundInfo {
        ensureNotificationChannel()
        val progress = if (totalBytes > 0) {
            ((transferredBytes.coerceIn(0, totalBytes).toDouble() / totalBytes) * PROGRESS_MAX).toInt()
        } else {
            0
        }
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Загрузка в Nextcloud")
            .setContentText(progressText(transferredBytes, totalBytes))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(PROGRESS_MAX, progress, totalBytes <= 0)
            .build()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(notificationId(jobId), notification, serviceType)
    }

    private fun ensureNotificationChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Передача файлов",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun progressText(transferredBytes: Long, totalBytes: Long): String = if (totalBytes > 0) {
        "${formatMegabytes(transferredBytes)} из ${formatMegabytes(totalBytes)} МБ"
    } else {
        "Подготовка файла"
    }

    private fun formatMegabytes(bytes: Long): String = String.format("%.1f", bytes / BYTES_PER_MEGABYTE)

    private fun notificationId(jobId: String): Int = jobId.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1)

    private fun invalidInput(): Result = Result.failure(workDataOf(KEY_ERROR to "invalid_input"))

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_TRANSFERRED_BYTES = "transferred_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"

        private const val CHANNEL_ID = "nextcloud_uploads"
        private const val PROGRESS_MAX = 1_000
        private const val BYTES_PER_MEGABYTE = 1024.0 * 1024.0
    }
}

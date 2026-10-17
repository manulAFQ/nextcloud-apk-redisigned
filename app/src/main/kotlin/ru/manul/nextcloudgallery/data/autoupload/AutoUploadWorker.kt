package ru.manul.nextcloudgallery.data.autoupload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

interface AutoUploadWorkerDependencies {
    val autoUploadRunner: AutoUploadRunner
}

class AutoUploadWorker(appContext: Context, parameters: WorkerParameters) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getString(KEY_ACCOUNT_ID)
            ?: return Result.failure(workDataOf(KEY_ERROR to "invalid_input"))
        val dependencies = applicationContext as? AutoUploadWorkerDependencies
            ?: return Result.failure(workDataOf(KEY_ERROR to "missing_dependencies"))
        return when (val decision = dependencies.autoUploadRunner.run(accountId)) {
            is AutoUploadRunDecision.Success -> Result.success(
                workDataOf(KEY_DISCOVERED to decision.discovered, KEY_ENQUEUED to decision.enqueued),
            )
            AutoUploadRunDecision.Disabled -> Result.success()
            is AutoUploadRunDecision.Retry -> Result.retry()
            is AutoUploadRunDecision.Failure -> Result.failure(
                workDataOf(KEY_ERROR to decision.failure::class.simpleName.orEmpty()),
            )
        }
    }

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_DISCOVERED = "discovered"
        const val KEY_ENQUEUED = "enqueued"
        const val KEY_ERROR = "error"
    }
}

package ru.manul.nextcloudgallery.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ru.manul.nextcloudgallery.domain.sync.SyncOutcome

interface SyncWorkerDependencies {
    val syncWorkerRunner: SyncWorkerRunner
}

class MediaSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getString(KEY_ACCOUNT_ID) ?: return invalidInput()
        val remotePath = inputData.getString(KEY_REMOTE_PATH) ?: return invalidInput()
        val dependencies = applicationContext as? SyncWorkerDependencies
            ?: return Result.failure(workDataOf(KEY_ERROR to "missing_dependencies"))

        setProgress(workDataOf(KEY_PHASE to PHASE_CHECKING))
        return when (
            val decision = dependencies.syncWorkerRunner.run(
                expectedAccountId = accountId,
                remotePath = remotePath,
                force = inputData.getBoolean(KEY_FORCE, false),
            )
        ) {
            is SyncRunDecision.Success -> Result.success(successData(decision.outcome))
            is SyncRunDecision.Retry -> Result.retry()
            is SyncRunDecision.Failure -> Result.failure(
                workDataOf(KEY_ERROR to decision.failure::class.simpleName.orEmpty()),
            )
        }
    }

    private fun invalidInput(): Result = Result.failure(workDataOf(KEY_ERROR to "invalid_input"))

    private fun successData(outcome: SyncOutcome?): Data = when (outcome) {
        null -> workDataOf(KEY_OUTCOME to OUTCOME_NO_SESSION)
        SyncOutcome.AlreadyRunning -> workDataOf(KEY_OUTCOME to OUTCOME_ALREADY_RUNNING)
        is SyncOutcome.Unchanged -> workDataOf(
            KEY_OUTCOME to OUTCOME_UNCHANGED,
            KEY_CHECKED_AT to outcome.checkedAtEpochMs,
        )
        is SyncOutcome.Updated -> workDataOf(
            KEY_OUTCOME to OUTCOME_UPDATED,
            KEY_ITEM_COUNT to outcome.itemCount,
            KEY_GENERATION to outcome.generation,
        )
    }

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_REMOTE_PATH = "remote_path"
        const val KEY_FORCE = "force"
        const val KEY_PHASE = "phase"
        const val KEY_OUTCOME = "outcome"
        const val KEY_ERROR = "error"
        const val KEY_ITEM_COUNT = "item_count"
        const val KEY_GENERATION = "generation"
        const val KEY_CHECKED_AT = "checked_at_epoch_ms"

        const val PHASE_CHECKING = "checking"
        const val OUTCOME_UPDATED = "updated"
        const val OUTCOME_UNCHANGED = "unchanged"
        const val OUTCOME_ALREADY_RUNNING = "already_running"
        const val OUTCOME_NO_SESSION = "no_session"
    }
}

package ru.manul.nextcloudgallery.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadJobDao {
    @Query("SELECT * FROM upload_jobs WHERE account_id = :accountId ORDER BY created_at_epoch_ms DESC")
    fun observeForAccount(accountId: String): Flow<List<UploadJobEntity>>

    @Query("SELECT * FROM upload_jobs WHERE job_id = :jobId LIMIT 1")
    suspend fun get(jobId: String): UploadJobEntity?

    @Query(
        """
        SELECT * FROM upload_jobs
        WHERE account_id = :accountId
          AND state IN (:states)
          AND (next_attempt_epoch_ms IS NULL OR next_attempt_epoch_ms <= :nowEpochMs)
        ORDER BY created_at_epoch_ms ASC
        LIMIT :limit
        """,
    )
    suspend fun findRunnable(
        accountId: String,
        states: List<String>,
        nowEpochMs: Long,
        limit: Int,
    ): List<UploadJobEntity>

    @Query(
        """
        SELECT * FROM upload_jobs
        WHERE account_id = :accountId AND state IN (:states)
        ORDER BY created_at_epoch_ms ASC
        """,
    )
    suspend fun findRestorable(
        accountId: String,
        states: List<String>,
    ): List<UploadJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: UploadJobEntity)

    @Query(
        """
        UPDATE upload_jobs
        SET state = 'RUNNING',
            transferred_bytes = 0,
            attempt = attempt + 1,
            failure_message = NULL,
            next_attempt_epoch_ms = NULL,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE job_id = :jobId
          AND state IN ('QUEUED', 'RUNNING')
        """,
    )
    suspend fun markRunning(
        jobId: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE upload_jobs
        SET remote_path = :remotePath,
            destination_resolved = 1,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE job_id = :jobId
          AND destination_resolved = 0
          AND state IN ('QUEUED', 'RUNNING')
        """,
    )
    suspend fun resolveDestination(jobId: String, remotePath: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE upload_jobs
        SET transferred_bytes = :transferredBytes,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE job_id = :jobId AND state = 'RUNNING'
        """,
    )
    suspend fun updateProgress(jobId: String, transferredBytes: Long, updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE upload_jobs
        SET state = 'SUCCEEDED',
            transferred_bytes = :transferredBytes,
            result_etag = :resultEtag,
            failure_message = NULL,
            next_attempt_epoch_ms = NULL,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE job_id = :jobId AND state IN ('RUNNING', 'PAUSED')
        """,
    )
    suspend fun markSucceeded(
        jobId: String,
        transferredBytes: Long,
        resultEtag: String?,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE upload_jobs
        SET state = 'QUEUED',
            transferred_bytes = :transferredBytes,
            failure_message = :failureMessage,
            next_attempt_epoch_ms = :nextAttemptEpochMs,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE job_id = :jobId AND state = 'RUNNING'
        """,
    )
    suspend fun markRetryable(
        jobId: String,
        transferredBytes: Long,
        failureMessage: String,
        nextAttemptEpochMs: Long,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE upload_jobs
        SET state = 'FAILED',
            transferred_bytes = :transferredBytes,
            failure_message = :failureMessage,
            next_attempt_epoch_ms = NULL,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE job_id = :jobId AND state = 'RUNNING'
        """,
    )
    suspend fun markFailed(
        jobId: String,
        transferredBytes: Long,
        failureMessage: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE upload_jobs
        SET state = 'PAUSED',
            failure_message = NULL,
            next_attempt_epoch_ms = NULL,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE job_id = :jobId AND state IN ('QUEUED', 'RUNNING')
        """,
    )
    suspend fun pause(jobId: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE upload_jobs
        SET state = 'QUEUED',
            transferred_bytes = 0,
            failure_message = NULL,
            next_attempt_epoch_ms = NULL,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE job_id = :jobId AND state IN ('PAUSED', 'FAILED')
        """,
    )
    suspend fun queueAgain(jobId: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE upload_jobs
        SET state = 'QUEUED',
            transferred_bytes = 0,
            next_attempt_epoch_ms = NULL,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE account_id = :accountId
          AND state = 'RUNNING'
          AND updated_at_epoch_ms <= :staleBeforeEpochMs
        """,
    )
    suspend fun recoverInterrupted(
        accountId: String,
        staleBeforeEpochMs: Long,
        updatedAtEpochMs: Long,
    ): Int

    @Query("DELETE FROM upload_jobs WHERE job_id = :jobId")
    suspend fun delete(jobId: String)
}

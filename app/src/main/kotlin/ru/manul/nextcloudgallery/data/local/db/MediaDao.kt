package ru.manul.nextcloudgallery.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query(
        """
        SELECT * FROM media
        WHERE account_id = :accountId AND parent_path = :parentPath
        ORDER BY sort_epoch_ms DESC, remote_path ASC
        """,
    )
    fun observeFolder(accountId: String, parentPath: String): Flow<List<MediaEntity>>

    @Query(
        """
        SELECT * FROM media
        WHERE account_id = :accountId AND parent_path = :parentPath
        ORDER BY sort_epoch_ms DESC, remote_path ASC
        """,
    )
    fun pagingSource(accountId: String, parentPath: String): PagingSource<Int, MediaEntity>

    @Query(
        """
        SELECT * FROM media
        WHERE account_id = :accountId AND parent_path = :parentPath
        ORDER BY sort_epoch_ms DESC, remote_path ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun page(
        accountId: String,
        parentPath: String,
        limit: Int,
        offset: Int,
    ): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaEntity>)

    @Query(
        """
        DELETE FROM media
        WHERE account_id = :accountId
          AND parent_path = :parentPath
          AND sync_generation != :syncGeneration
        """,
    )
    suspend fun deleteMissingFromGeneration(
        accountId: String,
        parentPath: String,
        syncGeneration: Long,
    )

    @Query("DELETE FROM media WHERE account_id = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

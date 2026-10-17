package ru.manul.nextcloudgallery.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncCursorDao {
    @Query(
        "SELECT * FROM sync_cursors WHERE account_id = :accountId AND remote_path = :remotePath LIMIT 1",
    )
    suspend fun get(accountId: String, remotePath: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query("DELETE FROM sync_cursors WHERE account_id = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

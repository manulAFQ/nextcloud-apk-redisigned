package ru.manul.nextcloudgallery.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AccountDao {
    @Query("SELECT * FROM accounts ORDER BY updated_at_epoch_ms DESC")
    abstract fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE account_id = :accountId LIMIT 1")
    abstract suspend fun get(accountId: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE active = 1 LIMIT 1")
    abstract suspend fun getActive(): AccountEntity?

    @Upsert
    protected abstract suspend fun upsertInternal(account: AccountEntity)

    @Query("UPDATE accounts SET active = 0 WHERE active = 1")
    protected abstract suspend fun deactivateAll()

    @Transaction
    open suspend fun activate(account: AccountEntity) {
        deactivateAll()
        upsertInternal(account.copy(active = true))
    }

    @Upsert
    abstract suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE account_id = :accountId")
    abstract suspend fun delete(accountId: String)
}

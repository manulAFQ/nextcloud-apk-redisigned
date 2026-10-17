package ru.manul.nextcloudgallery.domain.repository

import ru.manul.nextcloudgallery.core.model.Account

fun interface AccountIndex {
    suspend fun activate(account: Account, updatedAtEpochMs: Long)
}

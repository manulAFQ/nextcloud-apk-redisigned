package ru.manul.nextcloudgallery.data.autoupload

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.manul.nextcloudgallery.core.model.AutoUploadPolicy
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.AutoUploadPolicyStore
import ru.manul.nextcloudgallery.domain.sync.SessionLifecycleObserver

class AutoUploadManager(
    private val scope: CoroutineScope,
    private val policies: AutoUploadPolicyStore,
    private val scheduler: AutoUploadScheduler,
    private val observer: MediaStoreObserver,
) : SessionLifecycleObserver {
    suspend fun update(policy: AutoUploadPolicy): AppResult<Unit> = when (val saved = policies.save(policy)) {
        is AppResult.Failure -> saved
        is AppResult.Success -> {
            if (policy.enabled) {
                observer.start(policy)
                scheduler.schedule(policy)
            } else {
                observer.stop()
                scheduler.cancel(policy.accountId)
            }
            AppResult.Success(Unit)
        }
    }

    override fun onSessionAvailable(session: UserSession) {
        scope.launch {
            val policy = when (val stored = policies.get(session.account.id)) {
                is AppResult.Failure -> return@launch
                is AppResult.Success -> stored.value
            } ?: return@launch
            if (policy.enabled) {
                observer.start(policy)
                scheduler.schedule(policy)
            }
        }
    }

    override fun onSessionRemoved(accountId: String) {
        observer.stop()
        scheduler.cancel(accountId)
    }
}

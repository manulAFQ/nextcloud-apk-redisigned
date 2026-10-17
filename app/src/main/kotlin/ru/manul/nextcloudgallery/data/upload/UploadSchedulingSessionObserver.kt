package ru.manul.nextcloudgallery.data.upload

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.domain.repository.UploadQueueRepository
import ru.manul.nextcloudgallery.domain.sync.SessionLifecycleObserver

class UploadSchedulingSessionObserver(
    private val scope: CoroutineScope,
    private val repository: UploadQueueRepository,
    private val scheduler: UploadScheduler,
) : SessionLifecycleObserver {
    override fun onSessionAvailable(session: UserSession) {
        scope.launch { repository.restore(session.account.id) }
    }

    override fun onSessionRemoved(accountId: String) {
        scheduler.cancelForAccount(accountId)
    }
}

package ru.manul.nextcloudgallery.domain.sync

import ru.manul.nextcloudgallery.core.model.UserSession

interface SessionLifecycleObserver {
    fun onSessionAvailable(session: UserSession)

    fun onSessionRemoved(accountId: String)

    data object None : SessionLifecycleObserver {
        override fun onSessionAvailable(session: UserSession) = Unit

        override fun onSessionRemoved(accountId: String) = Unit
    }
}

class CompositeSessionLifecycleObserver(
    private vararg val observers: SessionLifecycleObserver,
) : SessionLifecycleObserver {
    override fun onSessionAvailable(session: UserSession) {
        observers.forEach { it.onSessionAvailable(session) }
    }

    override fun onSessionRemoved(accountId: String) {
        observers.forEach { it.onSessionRemoved(accountId) }
    }
}

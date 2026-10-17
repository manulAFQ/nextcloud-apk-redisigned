package ru.manul.nextcloudgallery

import android.app.Application
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.AndroidKeystoreSessionStore
import ru.manul.nextcloudgallery.data.local.AndroidUploadSource
import ru.manul.nextcloudgallery.data.local.DiskPreviewCache
import ru.manul.nextcloudgallery.data.local.SessionStore
import ru.manul.nextcloudgallery.data.local.db.GalleryDatabase
import ru.manul.nextcloudgallery.data.local.db.RoomAccountIndex
import ru.manul.nextcloudgallery.data.local.db.RoomMediaIndex
import ru.manul.nextcloudgallery.data.local.db.RoomUploadQueueStore
import ru.manul.nextcloudgallery.data.local.db.RoomAutoUploadStore
import ru.manul.nextcloudgallery.data.local.AndroidMediaStoreSource
import ru.manul.nextcloudgallery.data.autoupload.AndroidAutoUploadEnvironment
import ru.manul.nextcloudgallery.data.autoupload.AutoUploadManager
import ru.manul.nextcloudgallery.data.autoupload.AutoUploadRunner
import ru.manul.nextcloudgallery.data.autoupload.AutoUploadWorkerDependencies
import ru.manul.nextcloudgallery.data.autoupload.MediaStoreObserver
import ru.manul.nextcloudgallery.data.autoupload.WorkManagerAutoUploadScheduler
import ru.manul.nextcloudgallery.data.remote.OkHttpAuthGateway
import ru.manul.nextcloudgallery.data.remote.NextcloudMediaContentGateway
import ru.manul.nextcloudgallery.data.remote.WebDavUploadGateway
import ru.manul.nextcloudgallery.data.remote.WebDavMediaGateway
import ru.manul.nextcloudgallery.data.repository.DefaultAuthRepository
import ru.manul.nextcloudgallery.data.repository.DefaultMediaContentRepository
import ru.manul.nextcloudgallery.data.repository.DefaultUploadQueueRepository
import ru.manul.nextcloudgallery.data.repository.DefaultAccountRepository
import ru.manul.nextcloudgallery.data.remote.OcsNextcloudGateway
import ru.manul.nextcloudgallery.data.remote.NextcloudSectionsGateway
import ru.manul.nextcloudgallery.domain.repository.AccountRepository
import ru.manul.nextcloudgallery.domain.repository.NextcloudSectionsRepository
import ru.manul.nextcloudgallery.domain.repository.ServerRepository
import ru.manul.nextcloudgallery.data.repository.DefaultServerRepository
import ru.manul.nextcloudgallery.data.remote.OkHttpNextcloudGateway
import ru.manul.nextcloudgallery.data.local.AutoUploadPolicyStore
import ru.manul.nextcloudgallery.data.local.LocalMediaSource
import ru.manul.nextcloudgallery.data.sync.DefaultMediaSynchronizer
import ru.manul.nextcloudgallery.data.sync.MediaSyncScheduler
import ru.manul.nextcloudgallery.data.sync.SyncScheduler
import ru.manul.nextcloudgallery.data.sync.SyncWorkerDependencies
import ru.manul.nextcloudgallery.data.sync.SyncWorkerRunner
import ru.manul.nextcloudgallery.data.sync.WorkSchedulingSessionObserver
import ru.manul.nextcloudgallery.data.upload.UploadScheduler
import ru.manul.nextcloudgallery.data.upload.UploadSchedulingSessionObserver
import ru.manul.nextcloudgallery.data.upload.UploadWorkerDependencies
import ru.manul.nextcloudgallery.data.upload.UploadWorkerRunner
import ru.manul.nextcloudgallery.data.upload.WorkManagerUploadScheduler
import ru.manul.nextcloudgallery.domain.repository.AuthRepository
import ru.manul.nextcloudgallery.domain.repository.MediaIndex
import ru.manul.nextcloudgallery.domain.repository.MediaContentRepository
import ru.manul.nextcloudgallery.domain.repository.UploadQueueRepository
import ru.manul.nextcloudgallery.domain.sync.CompositeSessionLifecycleObserver

class GalleryApplication : Application(), SyncWorkerDependencies, UploadWorkerDependencies, AutoUploadWorkerDependencies {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database by lazy { GalleryDatabase.create(this) }

    val sessionStore: SessionStore by lazy { AndroidKeystoreSessionStore(this) }

    val syncScheduler: SyncScheduler by lazy { MediaSyncScheduler(WorkManager.getInstance(this)) }

    val mediaIndex: MediaIndex by lazy { RoomMediaIndex(database) }

    private val accountIndex by lazy { RoomAccountIndex(database) }

    private val uploadQueueStore by lazy { RoomUploadQueueStore(database.uploadJobDao()) }

    private val uploadSource by lazy { AndroidUploadSource(contentResolver) }

    val autoUploadPolicyStore: AutoUploadPolicyStore by lazy { RoomAutoUploadStore(database.autoUploadDao()) }

    private val autoUploadStore by lazy { autoUploadPolicyStore as RoomAutoUploadStore }

    val localMediaSource: LocalMediaSource by lazy { AndroidMediaStoreSource(this) }

    val accountRepository: AccountRepository by lazy { DefaultAccountRepository(OcsNextcloudGateway()) }

    val sectionsRepository: NextcloudSectionsRepository by lazy { NextcloudSectionsGateway() }

    val serverRepository: ServerRepository by lazy { DefaultServerRepository(OkHttpNextcloudGateway()) }

    private val autoUploadScheduler by lazy {
        WorkManagerAutoUploadScheduler(WorkManager.getInstance(this))
    }

    private val mediaStoreObserver by lazy {
        MediaStoreObserver(contentResolver, autoUploadScheduler)
    }

    val autoUploadManager by lazy {
        AutoUploadManager(applicationScope, autoUploadStore, autoUploadScheduler, mediaStoreObserver)
    }

    private val uploadGateway by lazy { WebDavUploadGateway() }

    val uploadScheduler: UploadScheduler by lazy {
        WorkManagerUploadScheduler(WorkManager.getInstance(this))
    }

    val uploadQueueRepository: UploadQueueRepository by lazy {
        DefaultUploadQueueRepository(
            store = uploadQueueStore,
            source = uploadSource,
            accountIndex = accountIndex,
            scheduler = uploadScheduler,
            sessionStore = sessionStore,
            uploadGateway = uploadGateway,
            autoUploadRegistry = autoUploadStore,
        )
    }

    val mediaContentRepository: MediaContentRepository by lazy {
        DefaultMediaContentRepository(
            gateway = NextcloudMediaContentGateway(),
            previewCache = DiskPreviewCache(cacheDir.resolve("media-previews")),
        )
    }

    private val mediaSynchronizer by lazy {
        DefaultMediaSynchronizer(
            gateway = WebDavMediaGateway(),
            accountIndex = accountIndex,
            mediaIndex = mediaIndex,
        )
    }

    override val syncWorkerRunner: SyncWorkerRunner by lazy {
        SyncWorkerRunner(sessionStore, mediaSynchronizer)
    }

    override val uploadWorkerRunner: UploadWorkerRunner by lazy {
        UploadWorkerRunner(
            sessionStore = sessionStore,
            queueStore = uploadQueueStore,
            uploadSource = uploadSource,
            gateway = uploadGateway,
            autoUploadRegistry = autoUploadStore,
        )
    }

    override val autoUploadRunner: AutoUploadRunner by lazy {
        AutoUploadRunner(
            sessionStore = sessionStore,
            policyStore = autoUploadStore,
            registry = autoUploadStore,
            mediaSource = localMediaSource,
            environment = AndroidAutoUploadEnvironment(this),
            uploads = uploadQueueRepository,
        )
    }

    val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(
            gateway = OkHttpAuthGateway(),
            sessionStore = sessionStore,
            sessionLifecycleObserver = CompositeSessionLifecycleObserver(
                WorkSchedulingSessionObserver(syncScheduler),
                UploadSchedulingSessionObserver(applicationScope, uploadQueueRepository, uploadScheduler),
                autoUploadManager,
            ),
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val stored = sessionStore.load()
            if (stored is AppResult.Success) {
                stored.value?.let { session ->
                    syncScheduler.schedulePeriodic(session.account.id)
                    uploadQueueRepository.restore(session.account.id)
                    autoUploadManager.onSessionAvailable(session)
                }
            }
        }
    }
}

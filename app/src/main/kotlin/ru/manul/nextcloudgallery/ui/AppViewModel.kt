package ru.manul.nextcloudgallery.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.manul.nextcloudgallery.GalleryApplication
import ru.manul.nextcloudgallery.core.model.ActivityEvent
import ru.manul.nextcloudgallery.core.model.AutoUploadPolicy
import ru.manul.nextcloudgallery.core.model.LocalMediaCandidate
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.RemoteAlbum
import ru.manul.nextcloudgallery.core.model.TrashItem
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.model.UploadState
import ru.manul.nextcloudgallery.core.model.UserProfile
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.sync.SyncScheduler

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

data class ShareRequest(val uris: List<Uri>, val mimeType: String)

data class AppUiState(
    val booting: Boolean = true,
    val session: UserSession? = null,
    val loginInProgress: Boolean = false,
    val serverAddress: String = "",
    val message: String? = null,
    val media: List<MediaItem> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val mediaWindowOffset: Int = 0,
    val mediaEstimatedTotal: Int = MEDIA_INITIAL_ESTIMATE,
    val mediaHasMore: Boolean = true,
    val mediaPageLoading: Boolean = false,
    val mediaFocusIndex: Int? = null,
    val sharePreparing: Boolean = false,
    val sharePrepared: Int = 0,
    val shareTotal: Int = 0,
    val localMedia: List<LocalMediaCandidate> = emptyList(),
    val uploads: List<UploadJob> = emptyList(),
    val profile: UserProfile? = null,
    val events: List<ActivityEvent> = emptyList(),
    val trash: List<TrashItem> = emptyList(),
    val albums: List<RemoteAlbum> = emptyList(),
    val albumFiles: Map<String, List<MediaItem>> = emptyMap(),
    val autoUpload: AutoUploadPolicy? = null,
    val lastSyncEpochMs: Long? = null,
    val refreshing: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
)

private const val MEDIA_PAGE_SIZE = 500
private const val MEDIA_INITIAL_ESTIMATE = 2_000

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GalleryApplication
    private val uiPreferences = application.getSharedPreferences(UI_PREFERENCES, 0)
    private val mutableState = MutableStateFlow(
        AppUiState(themeMode = uiPreferences.getString(THEME_MODE, null)
            ?.let { saved -> AppThemeMode.entries.firstOrNull { it.name == saved } }
            ?: AppThemeMode.SYSTEM),
    )
    private val uiExceptionHandler = CoroutineExceptionHandler { _, error ->
        mutableState.update {
            it.copy(
                booting = false,
                loginInProgress = false,
                refreshing = false,
                mediaPageLoading = false,
                sharePreparing = false,
                message = "Не удалось завершить операцию: ${error.message?.take(120) ?: "внутренняя ошибка"}",
            )
        }
    }
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private val mutableOpenUrl = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openUrl: SharedFlow<String> = mutableOpenUrl.asSharedFlow()
    private val mutableShareRequest = MutableSharedFlow<ShareRequest>(extraBufferCapacity = 1)
    val shareRequest: SharedFlow<ShareRequest> = mutableShareRequest.asSharedFlow()
    private var mediaJob: Job? = null
    private var uploadsJob: Job? = null

    init {
        viewModelScope.launch(uiExceptionHandler) {
            when (val stored = app.sessionStore.load()) {
                is AppResult.Success -> {
                    mutableState.update { it.copy(booting = false, session = stored.value) }
                    stored.value?.let(::attachSession)
                }
                is AppResult.Failure -> mutableState.update { it.copy(booting = false, message = stored.error.userMessage()) }
            }
        }
    }

    fun setServerAddress(value: String) = mutableState.update { it.copy(serverAddress = value, message = null) }

    fun login() {
        if (mutableState.value.loginInProgress) return
        viewModelScope.launch(uiExceptionHandler) {
            mutableState.update { it.copy(loginInProgress = true, message = null) }
            when (val checked = app.serverRepository.check(mutableState.value.serverAddress)) {
                is AppResult.Failure -> {
                    val text = if (checked.error is AppFailure.Http && checked.error.statusCode == 404) {
                        "По этому адресу Nextcloud не найден. Проверьте адрес сервера и путь установки."
                    } else {
                        checked.error.userMessage()
                    }
                    mutableState.update { it.copy(loginInProgress = false, message = text) }
                    return@launch
                }
                is AppResult.Success -> if (!checked.value.installed || checked.value.maintenance) {
                    mutableState.update {
                        it.copy(
                            loginInProgress = false,
                            message = if (checked.value.maintenance) "Nextcloud сейчас на обслуживании" else "Установка Nextcloud не завершена",
                        )
                    }
                    return@launch
                }
            }
            when (val challenge = app.authRepository.startLogin(mutableState.value.serverAddress)) {
                is AppResult.Failure -> mutableState.update { it.copy(loginInProgress = false, message = challenge.error.userMessage()) }
                is AppResult.Success -> {
                    mutableOpenUrl.emit(challenge.value.loginUrl)
                    when (val result = app.authRepository.awaitLogin(challenge.value)) {
                        is AppResult.Failure -> mutableState.update { it.copy(loginInProgress = false, message = result.error.userMessage()) }
                        is AppResult.Success -> {
                            mutableState.update { it.copy(loginInProgress = false, session = result.value) }
                            attachSession(result.value)
                        }
                    }
                }
            }
        }
    }

    private fun attachSession(session: UserSession) {
        mediaJob?.cancel()
        uploadsJob?.cancel()
        uploadsJob = viewModelScope.launch(uiExceptionHandler) {
            var previousStates: Map<String, UploadState>? = null
            app.uploadQueueRepository.observe(session.account.id).collectLatest { jobs ->
                mutableState.update { it.copy(uploads = jobs) }
                val completedNow = previousStates?.let { previous ->
                    jobs.any { job ->
                        job.state == UploadState.SUCCEEDED && previous[job.id] != UploadState.SUCCEEDED
                    }
                } == true
                previousStates = jobs.associate { it.id to it.state }
                if (completedNow) {
                    // A successful PUT is already committed on the server, but the
                    // photo feed is a separate DAV SEARCH. Reload it once after the
                    // transfer transition so the new item appears without reopening
                    // the application or navigating away from the screen.
                    viewModelScope.launch(uiExceptionHandler) {
                        delay(350)
                        refreshAll(forceSync = false)
                    }
                }
            }
        }
        loadLocalMedia()
        refreshAll(forceSync = false)
    }

    fun refreshAll(forceSync: Boolean = true) {
        val storedSession = mutableState.value.session ?: return
        viewModelScope.launch(uiExceptionHandler) {
            mutableState.update { it.copy(refreshing = true, message = null) }
            val profile = app.accountRepository.profile(storedSession).successOrNull()
            val session = if (!profile?.userId.isNullOrBlank() && profile.userId != storedSession.account.userId) {
                storedSession.copy(account = storedSession.account.copy(userId = profile.userId)).also {
                    app.sessionStore.save(it)
                }
            } else {
                storedSession
            }
            if (forceSync) app.syncScheduler.enqueueImmediate(session.account.id, force = true)
            val mediaResult = app.sectionsRepository.media(session, limit = MEDIA_PAGE_SIZE, offset = 0)
            val media = mediaResult.successOrNull()
            val favorites = app.sectionsRepository.favorites(session, limit = MEDIA_PAGE_SIZE).successOrNull()
            val events = app.sectionsRepository.activities(session).successOrNull()
            val trash = app.sectionsRepository.trash(session).successOrNull()
            val albumsResult = app.sectionsRepository.albums(session)
            val albums = albumsResult.successOrNull()
            val policy = app.autoUploadPolicyStore.get(session.account.id).successOrNull()
            val cursor = app.mediaIndex.cursor(session.account.id, SyncScheduler.DEFAULT_MEDIA_PATH)
            val loadedAlbums = buildMap {
                val actualById = (media ?: mutableState.value.media)
                    .mapNotNull { item -> item.fileId?.let { it to item } }
                    .toMap()
                albums.orEmpty().forEach { album ->
                    app.sectionsRepository.albumMedia(session, album.remotePath).successOrNull()?.let { albumItems ->
                        put(album.remotePath, albumItems.map { item -> actualById[item.fileId] ?: item })
                    }
                }
            }
            mutableState.update {
                it.copy(
                    session = session,
                    profile = profile ?: it.profile,
                    media = media ?: it.media,
                    favorites = favorites ?: it.favorites,
                    mediaWindowOffset = if (media != null) 0 else it.mediaWindowOffset,
                    mediaEstimatedTotal = if (media != null && media.size < MEDIA_PAGE_SIZE) {
                        media.size
                    } else {
                        maxOf(it.mediaEstimatedTotal, MEDIA_INITIAL_ESTIMATE)
                    },
                    mediaHasMore = media?.let { it.size == MEDIA_PAGE_SIZE } ?: it.mediaHasMore,
                    mediaPageLoading = false,
                    mediaFocusIndex = null,
                    events = events ?: it.events,
                    trash = trash ?: it.trash,
                    albums = albums ?: it.albums,
                    albumFiles = if (loadedAlbums.isNotEmpty()) loadedAlbums else it.albumFiles,
                    autoUpload = policy ?: it.autoUpload,
                    lastSyncEpochMs = cursor?.lastSyncEpochMs,
                    message = when {
                        mediaResult is AppResult.Failure -> "Фотографии: ${mediaResult.error.userMessage()}"
                        albumsResult is AppResult.Failure -> "Альбомы: ${albumsResult.error.userMessage()}"
                        else -> it.message
                    },
                    refreshing = false,
                )
            }
            if (policy?.enabled != true) stopActiveUploads()
            if (forceSync) {
                delay(1800)
                mutableState.update { current ->
                    val latest = app.mediaIndex.cursor(session.account.id, SyncScheduler.DEFAULT_MEDIA_PATH)
                    current.copy(lastSyncEpochMs = latest?.lastSyncEpochMs, refreshing = false)
                }
            }
        }
    }

    fun refreshVisibleContent() = refreshAll(forceSync = false)

    fun loadNextMediaChunk() {
        val snapshot = mutableState.value
        val session = snapshot.session ?: return
        if (snapshot.mediaPageLoading || !snapshot.mediaHasMore) return
        val expectedWindow = snapshot.mediaWindowOffset
        val offset = expectedWindow + snapshot.media.size
        viewModelScope.launch {
            mutableState.update { it.copy(mediaPageLoading = true) }
            when (val result = app.sectionsRepository.media(session, MEDIA_PAGE_SIZE, offset)) {
                is AppResult.Failure -> mutableState.update {
                    it.copy(mediaPageLoading = false, message = result.error.userMessage())
                }
                is AppResult.Success -> mutableState.update { current ->
                    if (current.mediaWindowOffset != expectedWindow) return@update current.copy(mediaPageLoading = false)
                    val merged = (current.media + result.value).distinctBy(MediaItem::remotePath)
                    val reachedEnd = result.value.size < MEDIA_PAGE_SIZE
                    current.copy(
                        media = merged,
                        mediaHasMore = !reachedEnd,
                        mediaEstimatedTotal = if (reachedEnd) offset + result.value.size
                            else maxOf(current.mediaEstimatedTotal, offset + MEDIA_PAGE_SIZE),
                        mediaPageLoading = false,
                    )
                }
            }
        }
    }

    fun loadMediaChunkAt(absoluteIndex: Int) {
        val snapshot = mutableState.value
        val session = snapshot.session ?: return
        val target = absoluteIndex.coerceIn(0, (snapshot.mediaEstimatedTotal - 1).coerceAtLeast(0))
        if (target in snapshot.mediaWindowOffset until (snapshot.mediaWindowOffset + snapshot.media.size)) {
            mutableState.update { it.copy(mediaFocusIndex = target) }
            return
        }
        if (snapshot.mediaPageLoading) return
        val requestedOffset = (target / MEDIA_PAGE_SIZE) * MEDIA_PAGE_SIZE
        viewModelScope.launch {
            mutableState.update { it.copy(mediaPageLoading = true, mediaFocusIndex = target) }
            var offset = requestedOffset
            var page: List<MediaItem> = emptyList()
            var failure: AppFailure? = null
            while (offset >= 0 && page.isEmpty()) {
                when (val result = app.sectionsRepository.media(session, MEDIA_PAGE_SIZE, offset)) {
                    is AppResult.Failure -> { failure = result.error; break }
                    is AppResult.Success -> page = result.value
                }
                if (page.isEmpty()) offset -= MEDIA_PAGE_SIZE
            }
            if (failure != null) {
                mutableState.update { it.copy(mediaPageLoading = false, message = failure!!.userMessage()) }
            } else {
                mutableState.update { current ->
                    val safeOffset = offset.coerceAtLeast(0)
                    val endReached = page.size < MEDIA_PAGE_SIZE
                    current.copy(
                        media = page,
                        mediaWindowOffset = safeOffset,
                        mediaHasMore = !endReached,
                        mediaEstimatedTotal = if (endReached) safeOffset + page.size else maxOf(current.mediaEstimatedTotal, safeOffset + MEDIA_PAGE_SIZE),
                        mediaPageLoading = false,
                        mediaFocusIndex = target.coerceIn(safeOffset, (safeOffset + page.lastIndex).coerceAtLeast(safeOffset)),
                    )
                }
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        uiPreferences.edit().putString(THEME_MODE, mode.name).apply()
        mutableState.update { it.copy(themeMode = mode) }
    }

    fun loadLocalMedia(includeVideos: Boolean = true) {
        viewModelScope.launch {
            when (val result = app.localMediaSource.scan(includeVideos, 2000)) {
                is AppResult.Success -> mutableState.update { it.copy(localMedia = result.value, message = null) }
                is AppResult.Failure -> mutableState.update { it.copy(message = result.error.userMessage()) }
            }
        }
    }

    fun enqueueUpload(uri: String, displayName: String) {
        val session = mutableState.value.session ?: return
        val safeName = displayName.substringAfterLast('/').ifBlank { "file-${System.currentTimeMillis()}" }
        viewModelScope.launch {
            when (val result = app.uploadQueueRepository.enqueue(session, uri, "/Photos/$safeName")) {
                is AppResult.Success -> mutableState.update { it.copy(message = "Файл загружается в Nextcloud · /Photos") }
                is AppResult.Failure -> mutableState.update { it.copy(message = result.error.userMessage()) }
            }
        }
    }

    fun uploadLocal(item: LocalMediaCandidate) = enqueueUpload(item.contentUri, item.displayName)

    fun createAlbum(name: String, items: List<MediaItem> = emptyList()) {
        val session = mutableState.value.session ?: return
        viewModelScope.launch {
            when (val created = app.sectionsRepository.createAlbum(session, name)) {
                is AppResult.Failure -> mutableState.update { it.copy(message = created.error.userMessage()) }
                is AppResult.Success -> {
                    val added = if (items.isEmpty()) AppResult.Success(Unit)
                    else app.sectionsRepository.addToAlbum(session, created.value, items)
                    if (added is AppResult.Failure) {
                        mutableState.update { it.copy(message = added.error.userMessage()) }
                    } else {
                        mutableState.update { it.copy(message = "Альбом «${created.value.name}» создан") }
                        refreshAll(forceSync = false)
                    }
                }
            }
        }
    }

    fun addToAlbum(album: RemoteAlbum, items: List<MediaItem>) {
        val session = mutableState.value.session ?: return
        if (items.isEmpty()) return
        viewModelScope.launch {
            when (val result = app.sectionsRepository.addToAlbum(session, album, items)) {
                is AppResult.Failure -> mutableState.update { it.copy(message = result.error.userMessage()) }
                is AppResult.Success -> {
                    mutableState.update { it.copy(message = "Фото добавлены в «${album.name}»") }
                    refreshAll(forceSync = false)
                }
            }
        }
    }

    fun deleteMedia(items: List<MediaItem>) {
        val session = mutableState.value.session ?: return
        if (items.isEmpty()) return
        viewModelScope.launch {
            when (val result = app.sectionsRepository.deleteMedia(session, items)) {
                is AppResult.Failure -> mutableState.update { it.copy(message = result.error.userMessage()) }
                is AppResult.Success -> {
                    val deleted = items.mapTo(hashSetOf(), MediaItem::remotePath)
                    mutableState.update { current ->
                        current.copy(
                            media = current.media.filterNot { it.remotePath in deleted },
                            favorites = current.favorites.filterNot { it.remotePath in deleted },
                            albumFiles = current.albumFiles.mapValues { (_, files) -> files.filterNot { it.remotePath in deleted } },
                            message = "Удалено: ${items.size}",
                        )
                    }
                    refreshAll(forceSync = false)
                }
            }
        }
    }

    fun setFavorite(item: MediaItem, favorite: Boolean) {
        val session = mutableState.value.session ?: return
        viewModelScope.launch {
            when (val result = app.sectionsRepository.setFavorite(session, item, favorite)) {
                is AppResult.Failure -> mutableState.update { it.copy(message = result.error.userMessage()) }
                is AppResult.Success -> mutableState.update { current ->
                    current.copy(
                        media = current.media.map { if (it.remotePath == item.remotePath) it.copy(favorite = favorite) else it },
                        favorites = if (favorite) {
                            (listOf(item.copy(favorite = true)) + current.favorites)
                                .distinctBy(MediaItem::remotePath)
                        } else {
                            current.favorites.filterNot { it.remotePath == item.remotePath }
                        },
                        albumFiles = current.albumFiles.mapValues { (_, files) ->
                            files.map { if (it.fileId == item.fileId) it.copy(favorite = favorite) else it }
                        },
                        message = if (favorite) "Добавлено в избранное" else "Удалено из избранного",
                    )
                }
            }
        }
    }

    fun shareMedia(items: List<MediaItem>) {
        val session = mutableState.value.session ?: return
        val selected = items.distinctBy(MediaItem::remotePath)
        if (selected.isEmpty()) return
        if (selected.size > 20) {
            mutableState.update { it.copy(message = "Поделиться можно максимум 20 файлами") }
            return
        }
        viewModelScope.launch {
            mutableState.update {
                it.copy(message = null, sharePreparing = true, sharePrepared = 0, shareTotal = selected.size)
            }
            val result = withContext(Dispatchers.IO) {
                val directory = File(getApplication<Application>().cacheDir, "shared-media").apply { mkdirs() }
                directory.listFiles()?.forEach(File::delete)
                val semaphore = Semaphore(4)
                val prepared = coroutineScope {
                    selected.mapIndexed { index, item ->
                        async {
                            semaphore.withPermit {
                                when (val opened = app.mediaContentRepository.openOriginal(session, item.remotePath)) {
                                    is AppResult.Failure -> opened
                                    is AppResult.Success -> runCatching {
                                        val safeName = item.displayName.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
                                            .ifBlank { "file" }
                                        val target = File(directory, "${index + 1}-$safeName")
                                        opened.value.use { stream -> target.outputStream().use(stream.input::copyTo) }
                                        FileProvider.getUriForFile(
                                            getApplication(),
                                            "${getApplication<Application>().packageName}.fileprovider",
                                            target,
                                        )
                                    }.fold(
                                        onSuccess = { AppResult.Success(it) },
                                        onFailure = { AppResult.Failure(AppFailure.LocalStorageUnavailable) },
                                    )
                                }.also {
                                    mutableState.update { state ->
                                        state.copy(sharePrepared = (state.sharePrepared + 1).coerceAtMost(state.shareTotal))
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }
                val failure = prepared.filterIsInstance<AppResult.Failure>().firstOrNull()?.error
                val uris = prepared.filterIsInstance<AppResult.Success<Uri>>().map(AppResult.Success<Uri>::value)
                failure?.let { AppResult.Failure(it) } ?: AppResult.Success(
                    ShareRequest(
                        uris = uris,
                        mimeType = selected.map(MediaItem::mimeType).distinct().singleOrNull()
                            ?: if (selected.all { it.mimeType.startsWith("image/") }) "image/*" else "*/*",
                    ),
                )
            }
            when (result) {
                is AppResult.Failure -> mutableState.update {
                    it.copy(sharePreparing = false, message = result.error.userMessage())
                }
                is AppResult.Success -> {
                    mutableState.update { it.copy(sharePreparing = false, message = null) }
                    mutableShareRequest.emit(result.value)
                }
            }
        }
    }

    fun downloadMedia(item: MediaItem, destination: Uri) {
        val session = mutableState.value.session ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(message = "Скачиваю ${item.displayName}…") }
            val result = withContext(Dispatchers.IO) {
                when (val opened = app.mediaContentRepository.openOriginal(session, item.remotePath)) {
                    is AppResult.Failure -> opened
                    is AppResult.Success -> runCatching {
                        val output = getApplication<Application>().contentResolver.openOutputStream(destination)
                            ?: error("destination unavailable")
                        opened.value.use { stream -> output.use(stream.input::copyTo) }
                    }.fold(
                        onSuccess = { AppResult.Success(Unit) },
                        onFailure = { AppResult.Failure(AppFailure.LocalStorageUnavailable) },
                    )
                }
            }
            mutableState.update {
                it.copy(message = if (result is AppResult.Success) "Файл сохранён" else (result as AppResult.Failure).error.userMessage())
            }
        }
    }

    fun uploadAction(job: UploadJob) {
        viewModelScope.launch {
            val result = when (job.state) {
                UploadState.RUNNING, UploadState.QUEUED -> app.uploadQueueRepository.pause(job.id)
                UploadState.PAUSED -> app.uploadQueueRepository.resume(job.id)
                UploadState.FAILED -> app.uploadQueueRepository.retry(job.id)
                UploadState.SUCCEEDED -> app.uploadQueueRepository.remove(job.id)
            }
            if (result is AppResult.Failure) mutableState.update { it.copy(message = result.error.userMessage()) }
        }
    }

    fun removeUpload(job: UploadJob) = viewModelScope.launch { app.uploadQueueRepository.remove(job.id) }

    fun updateAutoUpload(transform: (AutoUploadPolicy) -> AutoUploadPolicy) {
        val session = mutableState.value.session ?: return
        val current = mutableState.value.autoUpload ?: AutoUploadPolicy(
            accountId = session.account.id,
            deviceFolder = Build.MODEL.replace(Regex("[^A-Za-zА-Яа-я0-9._ -]"), "_").ifBlank { "Android" },
        )
        val changed = transform(current)
        val wasEnabled = current.enabled
        viewModelScope.launch {
            when (val result = app.autoUploadManager.update(changed)) {
                is AppResult.Success -> {
                    if (wasEnabled && !changed.enabled) stopActiveUploads()
                    mutableState.update {
                        it.copy(
                            autoUpload = changed,
                            message = if (wasEnabled && !changed.enabled) {
                                "Автозагрузка отключена, текущие передачи остановлены"
                            } else {
                                "Настройки синхронизации сохранены"
                            },
                        )
                    }
                }
                is AppResult.Failure -> mutableState.update { it.copy(message = result.error.userMessage()) }
            }
        }
    }

    fun setFolderSynchronized(relativePath: String, synchronized: Boolean) {
        updateAutoUpload { policy ->
            policy.copy(
                selectedFolders = if (synchronized) {
                    policy.selectedFolders + relativePath
                } else {
                    policy.selectedFolders - relativePath
                },
            )
        }
    }

    private suspend fun stopActiveUploads() {
        mutableState.value.uploads
            .filter { it.state == UploadState.QUEUED || it.state == UploadState.RUNNING }
            .forEach { app.uploadQueueRepository.pause(it.id) }
    }

    fun restore(item: TrashItem) {
        val session = mutableState.value.session ?: return
        viewModelScope.launch {
            when (val result = app.sectionsRepository.restore(session, item)) {
                is AppResult.Success -> {
                    mutableState.update { it.copy(trash = it.trash - item, message = "Файл восстановлен") }
                    refreshAll()
                }
                is AppResult.Failure -> mutableState.update { it.copy(message = result.error.userMessage()) }
            }
        }
    }

    fun deletePermanently(item: TrashItem) {
        val session = mutableState.value.session ?: return
        viewModelScope.launch {
            when (val result = app.sectionsRepository.deletePermanently(session, item)) {
                is AppResult.Success -> mutableState.update { it.copy(trash = it.trash - item, message = "Удалено окончательно") }
                is AppResult.Failure -> mutableState.update { it.copy(message = result.error.userMessage()) }
            }
        }
    }

    fun logout() {
        val session = mutableState.value.session ?: return
        viewModelScope.launch {
            when (val result = app.authRepository.logout(session)) {
                is AppResult.Success -> {
                    mediaJob?.cancel(); uploadsJob?.cancel()
                    mutableState.value = AppUiState(
                        booting = false,
                        serverAddress = session.account.serverAddress.value,
                        themeMode = mutableState.value.themeMode,
                    )
                }
                is AppResult.Failure -> mutableState.update { it.copy(message = result.error.userMessage()) }
            }
        }
    }

    fun clearMessage() = mutableState.update { it.copy(message = null) }

    private fun <T> AppResult<T>.successOrNull(): T? = (this as? AppResult.Success<T>)?.value

    private fun AppFailure.userMessage(): String = when (this) {
        AppFailure.NoNetwork, AppFailure.ServerUnavailable -> "Сервер недоступен. Проверьте Wi‑Fi и адрес Nextcloud"
        AppFailure.Unauthorized -> "Сессия истекла. Войдите заново"
        AppFailure.Forbidden -> "Nextcloud не разрешил эту операцию"
        AppFailure.MediaPermissionRequired -> "Разрешите приложению доступ к фото и видео"
        AppFailure.LocalContentUnavailable -> "Телефон не смог прочитать галерею. Откройте настройки доступа и попробуйте снова"
        AppFailure.InsufficientStorage -> "В облаке закончилось место"
        AppFailure.LoginTimedOut -> "Вход не подтверждён вовремя"
        is AppFailure.InvalidServerAddress -> "Неверный адрес сервера"
        is AppFailure.Http -> "Ошибка Nextcloud: HTTP ${statusCode}"
        is AppFailure.Unexpected -> safeMessage
        else -> "Не удалось выполнить операцию"
    }

    private companion object {
        const val UI_PREFERENCES = "ui_preferences"
        const val THEME_MODE = "theme_mode"
    }
}

package ru.manul.nextcloudgallery.data.autoupload

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import ru.manul.nextcloudgallery.core.model.AutoUploadPolicy

class MediaStoreObserver(
    private val resolver: ContentResolver,
    private val scheduler: AutoUploadScheduler,
) {
    private var observer: ContentObserver? = null

    fun start(policy: AutoUploadPolicy) {
        stop()
        if (!policy.enabled) return
        val candidate = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scheduler.runNow(policy)
            }
        }
        try {
            resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, candidate)
            resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, candidate)
            observer = candidate
        } catch (_: SecurityException) {
            runCatching { resolver.unregisterContentObserver(candidate) }
        }
    }

    fun stop() {
        observer?.let(resolver::unregisterContentObserver)
        observer = null
    }
}

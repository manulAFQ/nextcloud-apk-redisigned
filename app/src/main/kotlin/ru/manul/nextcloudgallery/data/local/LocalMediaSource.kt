package ru.manul.nextcloudgallery.data.local

import ru.manul.nextcloudgallery.core.model.LocalMediaCandidate
import ru.manul.nextcloudgallery.core.result.AppResult

interface LocalMediaSource {
    fun scan(includeVideos: Boolean, limit: Int = 2_000): AppResult<List<LocalMediaCandidate>>
}

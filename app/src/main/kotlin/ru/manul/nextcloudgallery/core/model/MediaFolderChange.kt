package ru.manul.nextcloudgallery.core.model

sealed interface MediaFolderChange {
    data class Snapshot(
        val directoryEtag: String?,
        val items: List<MediaItem>,
    ) : MediaFolderChange

    data class Unchanged(
        val directoryEtag: String,
    ) : MediaFolderChange
}

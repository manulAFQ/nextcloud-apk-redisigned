package ru.manul.nextcloudgallery.data.remote

import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

internal data class DavEntry(
    val href: String,
    val contentType: String?,
    val contentLength: Long,
    val lastModified: String?,
    val etag: String?,
    val fileId: String?,
    val hasPreview: Boolean,
    val isCollection: Boolean,
    val trashFilename: String? = null,
    val trashOriginalLocation: String? = null,
    val trashDeletionTime: Long? = null,
    val originalDateTime: Long? = null,
    val realPath: String? = null,
    val fileName: String? = null,
    val itemCount: Int? = null,
    val lastPhoto: Long? = null,
    val originalName: String? = null,
    val favorite: Boolean = false,
)

internal class WebDavMultiStatusParser {
    fun parse(input: InputStream): List<DavEntry> {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
        }
        // Android devices ship different SAX implementations. Some of them throw
        // SAXNotRecognizedException while configuring one of these optional
        // hardening flags. Treat every flag independently: an unsupported flag
        // must not make every otherwise valid DAV response unreadable.
        SECURE_FEATURES.forEach { (feature, enabled) ->
            runCatching { factory.setFeature(feature, enabled) }
        }
        val handler = MultiStatusHandler()
        factory.newSAXParser().parse(input, handler)
        return handler.entries
    }

    private class MultiStatusHandler : DefaultHandler() {
        val entries = mutableListOf<DavEntry>()
        private var response: MutableEntry? = null
        private var propstat: MutableProperties? = null
        private var currentElement = ""
        private val text = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            currentElement = elementName(localName, qName)
            text.setLength(0)
            when (currentElement) {
                "response" -> response = MutableEntry()
                "propstat" -> propstat = MutableProperties()
                "collection" -> propstat?.isCollection = true
            }
        }

        override fun characters(characters: CharArray, start: Int, length: Int) {
            text.append(characters, start, length)
        }

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
            InputSource(StringReader(""))

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = elementName(localName, qName)
            val value = text.toString().trim()
            when (name) {
                "href" -> response?.href = value
                "status" -> propstat?.status = value
                "getcontenttype" -> propstat?.contentType = value.takeIf(String::isNotBlank)
                "getcontentlength", "size" -> propstat?.contentLength = value.toLongOrNull() ?: 0L
                "getlastmodified" -> propstat?.lastModified = value.takeIf(String::isNotBlank)
                "getetag" -> propstat?.etag = value.takeIf(String::isNotBlank)
                "fileid" -> propstat?.fileId = value.takeIf(String::isNotBlank)
                "has-preview" -> propstat?.hasPreview = value.equals("true", true) || value == "1"
                "trashbin-filename" -> propstat?.trashFilename = value.takeIf(String::isNotBlank)
                "trashbin-original-location" -> propstat?.trashOriginalLocation = value.takeIf(String::isNotBlank)
                "trashbin-deletion-time" -> propstat?.trashDeletionTime = value.toLongOrNull()
                "metadata-photos-original_date_time" -> propstat?.originalDateTime = value.toLongOrNull()
                "realpath" -> propstat?.realPath = value.takeIf(String::isNotBlank)
                "file-name" -> propstat?.fileName = value.takeIf(String::isNotBlank)
                "nbItems" -> propstat?.itemCount = value.toIntOrNull()
                "last-photo" -> propstat?.lastPhoto = value.toLongOrNull()
                "original-name" -> propstat?.originalName = value.takeIf(String::isNotBlank)
                "favorite" -> propstat?.favorite = value == "1" || value.equals("true", true)
                "propstat" -> {
                    val properties = propstat
                    if (properties?.status?.contains(" 200 ") == true) response?.merge(properties)
                    propstat = null
                }
                "response" -> {
                    response?.toImmutable()?.let(entries::add)
                    response = null
                }
            }
            currentElement = ""
            text.setLength(0)
        }

        private fun elementName(localName: String?, qName: String?): String =
            localName?.takeIf(String::isNotBlank) ?: qName.orEmpty().substringAfter(':')
    }

    private class MutableEntry {
        var href: String = ""
        var contentType: String? = null
        var contentLength: Long = 0
        var lastModified: String? = null
        var etag: String? = null
        var fileId: String? = null
        var hasPreview: Boolean = false
        var isCollection: Boolean = false
        var trashFilename: String? = null
        var trashOriginalLocation: String? = null
        var trashDeletionTime: Long? = null
        var originalDateTime: Long? = null
        var realPath: String? = null
        var fileName: String? = null
        var itemCount: Int? = null
        var lastPhoto: Long? = null
        var originalName: String? = null
        var favorite: Boolean = false

        fun merge(properties: MutableProperties) {
            contentType = properties.contentType ?: contentType
            contentLength = properties.contentLength
            lastModified = properties.lastModified ?: lastModified
            etag = properties.etag ?: etag
            fileId = properties.fileId ?: fileId
            hasPreview = properties.hasPreview
            isCollection = properties.isCollection
            trashFilename = properties.trashFilename ?: trashFilename
            trashOriginalLocation = properties.trashOriginalLocation ?: trashOriginalLocation
            trashDeletionTime = properties.trashDeletionTime ?: trashDeletionTime
            originalDateTime = properties.originalDateTime ?: originalDateTime
            realPath = properties.realPath ?: realPath
            fileName = properties.fileName ?: fileName
            itemCount = properties.itemCount ?: itemCount
            lastPhoto = properties.lastPhoto ?: lastPhoto
            originalName = properties.originalName ?: originalName
            favorite = properties.favorite
        }

        fun toImmutable(): DavEntry? = href.takeIf(String::isNotBlank)?.let {
            DavEntry(
                href = it,
                contentType = contentType,
                contentLength = contentLength,
                lastModified = lastModified,
                etag = etag,
                fileId = fileId,
                hasPreview = hasPreview,
                isCollection = isCollection,
                trashFilename = trashFilename,
                trashOriginalLocation = trashOriginalLocation,
                trashDeletionTime = trashDeletionTime,
                originalDateTime = originalDateTime,
                realPath = realPath,
                fileName = fileName,
                itemCount = itemCount,
                lastPhoto = lastPhoto,
                originalName = originalName,
                favorite = favorite,
            )
        }
    }

    private class MutableProperties {
        var status: String = ""
        var contentType: String? = null
        var contentLength: Long = 0
        var lastModified: String? = null
        var etag: String? = null
        var fileId: String? = null
        var hasPreview: Boolean = false
        var isCollection: Boolean = false
        var trashFilename: String? = null
        var trashOriginalLocation: String? = null
        var trashDeletionTime: Long? = null
        var originalDateTime: Long? = null
        var realPath: String? = null
        var fileName: String? = null
        var itemCount: Int? = null
        var lastPhoto: Long? = null
        var originalName: String? = null
        var favorite: Boolean = false
    }

    private companion object {
        val SECURE_FEATURES = listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        )
    }
}

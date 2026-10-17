package ru.manul.nextcloudgallery.data.remote

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

internal class ResponseTooLargeException : IOException()

internal class LimitedInputStream(
    input: InputStream,
    private val limitBytes: Long,
) : FilterInputStream(input) {
    private var consumedBytes = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) count(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) count(read)
        return read
    }

    private fun count(bytes: Int) {
        consumedBytes += bytes
        if (consumedBytes > limitBytes) throw ResponseTooLargeException()
    }
}

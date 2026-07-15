package com.worklogai.app.core.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupArchiveStreamsTest {
    @Test
    fun `stream byte counter accepts an unknown-length input exactly at its configured limit`() {
        val output = ByteArrayOutputStream()
        val source = byteArrayOf(1, 2, 3, 4)

        BackupArchiveStreams.copyToOutput(ByteArrayInputStream(source), output, limit = source.size.toLong())

        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun `stream byte counter rejects an unknown-length input before writing beyond its limit`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchiveStreams.copyToOutput(
                ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                ByteArrayOutputStream(),
                limit = 4,
            )
        }
    }
}

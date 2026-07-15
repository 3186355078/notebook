package com.worklogai.app.core.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry

internal object BackupArchiveContract {
    const val MANIFEST_FILE = "manifest.json"
    const val ENTRIES_FILE = "data/work_entries.json"
    const val BLOCKS_FILE = "data/content_blocks.json"
    const val ATTACHMENTS_FILE = "data/attachments.json"
    const val SUMMARIES_FILE = "data/work_summaries.json"
    const val SETTINGS_FILE = "data/settings.json"
    const val ATTACHMENTS_ROOT = "attachments"
    const val BACKUP_CACHE_DIRECTORY = "worklog-backup"
    const val ZIP_EXTENSION = ".zip"
    const val SHA_256 = "SHA-256"
    const val COPY_BUFFER_SIZE = 32 * 1024
    const val MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024
    const val MAX_UNCOMPRESSED_ENTRY_BYTES = 100L * 1024 * 1024
    const val MAX_UNCOMPRESSED_TOTAL_BYTES = 1024L * 1024 * 1024
    const val MAX_ATTACHMENT_BYTES = 100L * 1024 * 1024
    const val MAX_JSON_BYTES = 100L * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 100_000
    const val MAX_ENTRIES = 100_000
    const val MAX_BLOCKS = 1_000_000
    const val MAX_ATTACHMENTS = 100_000
    const val MAX_SUMMARIES = 100_000
    const val MAX_COMPRESSION_RATIO = 100L
    const val MAX_PATH_LENGTH = 240
    const val IMAGE_HEADER_SIZE = 12
    const val MAX_TABLE_COLUMNS = 8
    const val MAX_TABLE_ROWS = 50
    const val JPEG_MIME_TYPE = "image/jpeg"
    const val PNG_MIME_TYPE = "image/png"
    const val WEBP_MIME_TYPE = "image/webp"
    const val HEIC_MIME_TYPE = "image/heic"
    const val HEIF_MIME_TYPE = "image/heif"
    val SUPPORTED_MIME_TYPES = setOf(JPEG_MIME_TYPE, PNG_MIME_TYPE, WEBP_MIME_TYPE, HEIC_MIME_TYPE, HEIF_MIME_TYPE)
}

internal object BackupArchivePaths {
    fun ZipEntry.isSafeFile(maxPathLength: Int = BackupArchiveContract.MAX_PATH_LENGTH): Boolean =
        !isDirectory && name.isKnownBackupPath(maxPathLength)

    fun ZipEntry.isSafeDirectory(): Boolean = isDirectory && name.isSafeDirectoryName()

    fun String.isSafeDirectoryName(): Boolean =
        this in
            setOf(
                "data/",
                "attachments/",
                "attachments/images/",
            )

    fun String.isBackupPath(): Boolean =
        isNotBlank() &&
            !startsWith("/") &&
            !contains('\\') &&
            !contains(':') &&
            none { character -> character.code < CONTROL_CHARACTER_LIMIT } &&
            split('/').none { it.isBlank() || it == "." || it == ".." }

    fun String.isKnownBackupPath(maxPathLength: Int = BackupArchiveContract.MAX_PATH_LENGTH): Boolean =
        isBackupPath() &&
            length <= maxPathLength &&
            (
                this == BackupArchiveContract.MANIFEST_FILE ||
                    this == BackupArchiveContract.ENTRIES_FILE ||
                    this == BackupArchiveContract.BLOCKS_FILE ||
                    this == BackupArchiveContract.ATTACHMENTS_FILE ||
                    this == BackupArchiveContract.SUMMARIES_FILE ||
                    this == BackupArchiveContract.SETTINGS_FILE ||
                    startsWith("${BackupArchiveContract.ATTACHMENTS_ROOT}/images/")
            )

    fun String.isManagedAttachmentPath(): Boolean =
        startsWith("images/") && isBackupPath() && length <= BackupArchiveContract.MAX_PATH_LENGTH

    fun Map<String, ZipEntry>.required(path: String): ZipEntry =
        get(path) ?: throw IllegalArgumentException("Missing backup file")

    fun attachmentEntryPath(relativePath: String): String = "${BackupArchiveContract.ATTACHMENTS_ROOT}/$relativePath"

    fun stageFile(
        root: File,
        relativePath: String,
    ): File {
        require(relativePath.isManagedAttachmentPath())
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relativePath).canonicalFile
        require(candidate.path.startsWith(canonicalRoot.path + File.separator))
        return candidate
    }

    fun moveDirectory(
        source: File,
        target: File,
    ) {
        target.parentFile?.mkdirs()
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private const val CONTROL_CHARACTER_LIMIT = 0x20
}

internal object BackupArchiveStreams {
    fun copyToFile(
        input: InputStream,
        destination: File,
        limit: Long,
    ) {
        destination.outputStream().use { output -> copyToOutput(input, output, limit) }
    }

    fun copyToOutput(
        input: InputStream,
        destination: OutputStream,
        limit: Long,
    ) {
        val buffer = ByteArray(BackupArchiveContract.COPY_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return
            copied += count
            require(copied <= limit)
            destination.write(buffer, 0, count)
        }
    }

    fun readBytes(
        input: InputStream,
        limit: Long,
    ): ByteArray =
        ByteArrayOutputStream().use { output ->
            copyToOutput(input, output, limit)
            output.toByteArray()
        }

    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance(BackupArchiveContract.SHA_256).digest(bytes))

    fun hex(bytes: ByteArray): String = bytes.toHex()

    fun readImageHeader(input: InputStream): ByteArray {
        val header = ByteArray(BackupArchiveContract.IMAGE_HEADER_SIZE)
        val count = input.read(header)
        return if (count <= 0) byteArrayOf() else header.copyOf(count)
    }

    fun matchesImageMimeType(
        header: ByteArray,
        mimeType: String,
    ): Boolean =
        when (mimeType) {
            BackupArchiveContract.JPEG_MIME_TYPE -> header.hasPrefix(JPEG_SIGNATURE)
            BackupArchiveContract.PNG_MIME_TYPE -> header.hasPrefix(PNG_SIGNATURE)
            BackupArchiveContract.WEBP_MIME_TYPE ->
                header.hasAsciiSegment(WEBP_RIFF_OFFSET, WEBP_RIFF) &&
                    header.hasAsciiSegment(WEBP_TYPE_OFFSET, WEBP_TYPE)
            BackupArchiveContract.HEIC_MIME_TYPE, BackupArchiveContract.HEIF_MIME_TYPE ->
                header.hasAsciiSegment(
                    HEIF_BRAND_OFFSET,
                    HEIF_FILE_TYPE_BOX,
                )
            else -> false
        }

    private fun ByteArray.hasPrefix(signature: ByteArray): Boolean =
        size >= signature.size && copyOfRange(0, signature.size).contentEquals(signature)

    private fun ByteArray.hasAsciiSegment(
        offset: Int,
        value: String,
    ): Boolean = size >= offset + value.length && copyOfRange(offset, offset + value.length).decodeToString() == value

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte ->
            "%02x".format(
                byte.toInt() and BYTE_MASK,
            )
        }

    private const val WEBP_RIFF_OFFSET = 0
    private const val WEBP_TYPE_OFFSET = 8
    private const val HEIF_BRAND_OFFSET = 4
    private const val WEBP_RIFF = "RIFF"
    private const val WEBP_TYPE = "WEBP"
    private const val HEIF_FILE_TYPE_BOX = "ftyp"
    private const val BYTE_MASK = 0xff
    private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}

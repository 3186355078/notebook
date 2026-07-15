package com.worklogai.app.core.attachment

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.worklogai.app.core.common.di.IoDispatcher
import com.worklogai.app.core.common.id.IdGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

@Suppress("TooManyFunctions") // Import, path validation, and cleanup form one private-file boundary.
class AndroidAttachmentFileStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val idGenerator: IdGenerator,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val config: ImageImportConfig = ImageImportConfig(),
    ) : AttachmentFileStore {
        private var fileOperations: AttachmentFileOperations = DefaultAttachmentFileOperations
        private var imageEncoder: ImageEncoder = BitmapImageEncoder
        private var currentTimeMillis: () -> Long = System::currentTimeMillis

        internal constructor(
            context: Context,
            idGenerator: IdGenerator,
            ioDispatcher: CoroutineDispatcher,
            config: ImageImportConfig,
            testDependencies: AttachmentFileStoreTestDependencies,
        ) : this(context, idGenerator, ioDispatcher, config) {
            fileOperations = testDependencies.fileOperations
            imageEncoder = testDependencies.imageEncoder
            currentTimeMillis = testDependencies.currentTimeMillis
        }

        override suspend fun importImage(sourceUri: Uri): Result<StoredImage> =
            withContext(ioDispatcher) {
                try {
                    Result.success(importImageInternal(sourceUri))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    Result.failure(AttachmentFileStoreException("Unable to import image"))
                } catch (_: IllegalArgumentException) {
                    Result.failure(AttachmentFileStoreException("Unable to import image"))
                } catch (_: IllegalStateException) {
                    Result.failure(AttachmentFileStoreException("Unable to import image"))
                } catch (_: SecurityException) {
                    Result.failure(AttachmentFileStoreException("Unable to import image"))
                }
            }

        override suspend fun delete(relativePath: String): Result<Unit> =
            withContext(ioDispatcher) {
                val file =
                    fileFor(relativePath)
                        ?: return@withContext Result.failure(AttachmentFileStoreException("Invalid attachment path"))
                try {
                    if (file.exists() && !fileOperations.delete(file)) {
                        throw IOException("Unable to delete attachment")
                    }
                    Result.success(Unit)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    Result.failure(AttachmentFileStoreException("Unable to delete attachment"))
                } catch (_: SecurityException) {
                    Result.failure(AttachmentFileStoreException("Unable to delete attachment"))
                }
            }

        override suspend fun exists(relativePath: String): Boolean =
            withContext(ioDispatcher) { fileFor(relativePath)?.isFile == true }

        override suspend fun cleanupOrphans(referencedPaths: Set<String>): CleanupResult =
            withContext(ioDispatcher) {
                val referenced = referencedPaths.filter(::isManagedRelativePath).toSet()
                var deletedCount = 0
                var failedCount = 0
                imagesDirectory().listFiles()?.forEach { file ->
                    if (!file.isFile) return@forEach
                    val relativePath = "images/${file.name}"
                    val staleTemp =
                        file.name.endsWith(TEMP_EXTENSION) &&
                            file.lastModified() < currentTimeMillis() - TEMP_MAX_AGE_MS
                    if (relativePath !in referenced && (!file.name.endsWith(TEMP_EXTENSION) || staleTemp)) {
                        if (fileOperations.delete(file)) deletedCount++ else failedCount++
                    }
                }
                CleanupResult(deletedCount, failedCount)
            }

        override fun fileFor(relativePath: String): File? {
            if (!isManagedRelativePath(relativePath)) {
                return null
            }
            val root = attachmentsDirectory().canonicalFile
            val candidate = File(root, relativePath).canonicalFile
            return candidate.takeIf { it.path.startsWith(root.path + File.separator) && !it.isDirectory }
        }

        private fun importImageInternal(sourceUri: Uri): StoredImage {
            val resolver = context.contentResolver
            val sourceMimeType = resolver.getType(sourceUri)
            require(sourceMimeType == null || sourceMimeType in SUPPORTED_MIME_TYPES) { "Unsupported image type" }
            require(hasSupportedImageHeader(resolver, sourceUri)) { "Unsupported image content" }
            val bounds = readBounds(resolver, sourceUri)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }

            val bitmap = decodeSampled(resolver, sourceUri, bounds)
            val rotated = rotateIfNeeded(bitmap, resolver, sourceUri)
            if (rotated !== bitmap) bitmap.recycle()
            val scaled = scaleDown(rotated)
            if (scaled !== rotated) rotated.recycle()

            val format =
                if (sourceMimeType == "image/png" || bounds.outMimeType == "image/png" || scaled.hasAlpha()) {
                    Bitmap.CompressFormat.PNG
                } else {
                    Bitmap.CompressFormat.JPEG
                }
            val extension = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
            val mimeType = if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
            val name = "${idGenerator.generate()}.$extension"
            val finalFile = File(imagesDirectory(), name)
            val temporaryFile = File(imagesDirectory(), "$name$TEMP_EXTENSION")
            try {
                check(!finalFile.exists()) { "Attachment target already exists" }
                fileOperations.openOutputStream(temporaryFile).use { output ->
                    check(imageEncoder.encode(scaled, format, config.quality, output)) { "Unable to compress image" }
                }
                check(temporaryFile.length() > 0L) { "Compressed image is empty" }
                check(fileOperations.moveAtomically(temporaryFile, finalFile)) { "Unable to finalize image" }
                check(finalFile.isFile) { "Image output missing" }
                return StoredImage(
                    relativePath = "images/$name",
                    mimeType = mimeType,
                    fileSize = finalFile.length(),
                    width = scaled.width,
                    height = scaled.height,
                )
            } finally {
                scaled.recycle()
                if (temporaryFile.exists()) fileOperations.delete(temporaryFile)
            }
        }

        private fun isManagedRelativePath(relativePath: String): Boolean =
            relativePath.isNotBlank() &&
                relativePath.startsWith("images/") &&
                !relativePath.contains("..") &&
                !relativePath.contains('\\')

        // Android and Robolectric decoders expose malformed images as RuntimeException.
        @Suppress("TooGenericExceptionCaught")
        private fun readBounds(
            resolver: ContentResolver,
            uri: Uri,
        ): BitmapFactory.Options =
            try {
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    resolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, this) }
                        ?: throw IOException("Unable to read image")
                }
            } catch (_: RuntimeException) {
                throw IOException("Unable to decode image")
            }

        private fun hasSupportedImageHeader(
            resolver: ContentResolver,
            uri: Uri,
        ): Boolean {
            val header = ByteArray(HEADER_SIZE)
            val read = resolver.openInputStream(uri)?.use { it.read(header) } ?: return false
            return header.isJpeg() || header.isPng() || header.isWebp() || header.isHeif()
        }

        // Android and Robolectric decoders expose malformed images as RuntimeException.
        @Suppress("TooGenericExceptionCaught")
        private fun decodeSampled(
            resolver: ContentResolver,
            uri: Uri,
            bounds: BitmapFactory.Options,
        ): Bitmap {
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            return try {
                resolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                } ?: throw IOException("Unable to decode image")
            } catch (_: RuntimeException) {
                throw IOException("Unable to decode image")
            }
        }

        private fun sampleSize(
            width: Int,
            height: Int,
        ): Int {
            var sample = 1
            while (width / sample > config.maxDimension * 2 || height / sample > config.maxDimension * 2) sample *= 2
            return sample
        }

        private fun rotateIfNeeded(
            bitmap: Bitmap,
            resolver: ContentResolver,
            uri: Uri,
        ): Bitmap {
            val orientation =
                resolver.openInputStream(uri)?.use { input ->
                    ExifInterface(
                        input,
                    ).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
                    ?: ExifInterface.ORIENTATION_NORMAL
            val degrees =
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> ROTATION_90_DEGREES
                    ExifInterface.ORIENTATION_ROTATE_180 -> ROTATION_180_DEGREES
                    ExifInterface.ORIENTATION_ROTATE_270 -> ROTATION_270_DEGREES
                    else -> 0f
                }
            if (degrees == 0f) return bitmap
            val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        private fun scaleDown(bitmap: Bitmap): Bitmap {
            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest <= config.maxDimension) return bitmap
            val ratio = config.maxDimension.toFloat() / longest
            return Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true,
            )
        }

        private fun attachmentsDirectory(): File = File(context.filesDir, ATTACHMENTS_DIRECTORY).apply { mkdirs() }

        private fun imagesDirectory(): File = File(attachmentsDirectory(), IMAGES_DIRECTORY).apply { mkdirs() }

        private companion object {
            const val ATTACHMENTS_DIRECTORY = "attachments"
            const val IMAGES_DIRECTORY = "images"
            const val TEMP_EXTENSION = ".tmp"
            const val TEMP_MAX_AGE_MS = 24 * 60 * 60 * 1000L
            const val HEADER_SIZE = 12
            const val ROTATION_90_DEGREES = 90f
            const val ROTATION_180_DEGREES = 180f
            const val ROTATION_270_DEGREES = 270f
            val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
        }
    }

@Suppress("MagicNumber") // Fixed binary signatures are protocol constants, not tunable values.
private fun ByteArray.isJpeg(): Boolean =
    size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

@Suppress("MagicNumber") // Fixed binary signatures are protocol constants, not tunable values.
private fun ByteArray.isPng(): Boolean =
    size >= 8 &&
        this[0] == 0x89.toByte() &&
        this[1] == 0x50.toByte() &&
        this[2] == 0x4E.toByte() &&
        this[3] == 0x47.toByte()

@Suppress("MagicNumber") // Fixed binary signatures are protocol constants, not tunable values.
private fun ByteArray.isWebp(): Boolean =
    size >= 12 &&
        copyOfRange(0, 4).decodeToString() == "RIFF" &&
        copyOfRange(8, 12).decodeToString() == "WEBP"

@Suppress("MagicNumber") // Fixed binary signatures are protocol constants, not tunable values.
private fun ByteArray.isHeif(): Boolean = size >= 12 && copyOfRange(4, 8).decodeToString() == "ftyp"

data class ImageImportConfig(
    val maxDimension: Int = 2048,
    val quality: Int = 85,
)

internal interface AttachmentFileOperations {
    fun openOutputStream(file: File): OutputStream

    fun moveAtomically(
        source: File,
        target: File,
    ): Boolean

    fun delete(file: File): Boolean
}

internal data class AttachmentFileStoreTestDependencies(
    val fileOperations: AttachmentFileOperations = DefaultAttachmentFileOperations,
    val imageEncoder: ImageEncoder = BitmapImageEncoder,
    val currentTimeMillis: () -> Long = System::currentTimeMillis,
)

internal object DefaultAttachmentFileOperations : AttachmentFileOperations {
    override fun openOutputStream(file: File): OutputStream = FileOutputStream(file)

    override fun moveAtomically(
        source: File,
        target: File,
    ): Boolean =
        runCatching {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            true
        }.getOrDefault(false)

    override fun delete(file: File): Boolean = file.delete()
}

internal fun interface ImageEncoder {
    fun encode(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int,
        output: OutputStream,
    ): Boolean
}

internal object BitmapImageEncoder : ImageEncoder {
    override fun encode(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int,
        output: OutputStream,
    ): Boolean = bitmap.compress(format, quality, output)
}

private class AttachmentFileStoreException(
    message: String,
) : IOException(message)

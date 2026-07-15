package com.worklogai.app.core.attachment

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.worklogai.app.core.common.id.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidAttachmentFileStoreTest {
    private lateinit var application: Application
    private lateinit var imagesDirectory: File

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        imagesDirectory =
            File(application.filesDir, "attachments/images").apply {
                mkdirs()
                listFiles()?.forEach(File::delete)
            }
    }

    @Test
    fun `imports jpeg with controlled relative path and decodable output`() =
        runTest {
            val source = createBitmapFile("jpeg-source", 80, 40, Bitmap.CompressFormat.JPEG)

            val stored = store().importImage(Uri.fromFile(source)).getOrThrow()
            val output = store().fileFor(stored.relativePath)

            assertTrue(stored.relativePath.startsWith("images/"))
            assertFalse(stored.relativePath.contains(source.path))
            assertEquals("image/jpeg", stored.mimeType)
            assertEquals(80, stored.width)
            assertEquals(40, stored.height)
            assertTrue(stored.fileSize > 0)
            assertTrue(output?.isFile == true)
            assertNotNull(BitmapFactory.decodeFile(output!!.path))
            source.delete()
        }

    @Test
    fun `imports transparent png without converting it to jpeg`() =
        runTest {
            val source = createBitmapFile("png-source", 20, 20, Bitmap.CompressFormat.PNG, transparent = true)

            val stored = store().importImage(Uri.fromFile(source)).getOrThrow()
            val decoded = BitmapFactory.decodeFile(store().fileFor(stored.relativePath)!!.path)

            assertEquals("image/png", stored.mimeType)
            assertNotNull(decoded)
            source.delete()
        }

    @Test
    fun `scales large input without enlarging small input`() =
        runTest {
            val large = createBitmapFile("large-source", 300, 60, Bitmap.CompressFormat.JPEG)
            val small = createBitmapFile("small-source", 40, 20, Bitmap.CompressFormat.JPEG)
            val fileStore = store()

            val scaled = fileStore.importImage(Uri.fromFile(large)).getOrThrow()
            val unchanged = fileStore.importImage(Uri.fromFile(small)).getOrThrow()

            assertTrue(maxOf(scaled.width, scaled.height) <= 100)
            assertEquals(100, scaled.width)
            assertEquals(20, scaled.height)
            assertEquals(40, unchanged.width)
            assertEquals(20, unchanged.height)
            assertNotEquals(scaled.relativePath, unchanged.relativePath)
            large.delete()
            small.delete()
        }

    @Test
    fun `rotates jpeg using exif orientation`() =
        runTest {
            val source = createBitmapFile("rotated-source", 80, 40, Bitmap.CompressFormat.JPEG)
            ExifInterface(source.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }

            val stored = store().importImage(Uri.fromFile(source)).getOrThrow()

            assertEquals(40, stored.width)
            assertEquals(80, stored.height)
            source.delete()
        }

    @Test
    fun `rejects zero byte and undecodable input without output`() =
        runTest {
            val empty = File(application.cacheDir, "empty-image").apply { writeBytes(byteArrayOf()) }
            val fake =
                File(application.cacheDir, "fake-image.jpg").apply {
                    writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
                }
            val fileStore = store()

            assertTrue(fileStore.importImage(Uri.fromFile(empty)).isFailure)
            assertTrue(fileStore.importImage(Uri.fromFile(fake)).isFailure)
            assertTrue(imagesDirectory.listFiles().isNullOrEmpty())
            empty.delete()
            fake.delete()
        }

    @Test
    fun `rejects non image content even when its extension and mime are image like`() =
        runTest {
            val source = File(application.cacheDir, "not-an-image.jpg").apply { writeText("not image data") }

            val result = store().importImage(Uri.fromFile(source))

            assertTrue(result.isFailure)
            assertTrue(imagesDirectory.listFiles().isNullOrEmpty())
            source.delete()
        }

    @Test
    fun `output stream creation failure is sanitized and cleaned`() =
        runTest {
            val source = createBitmapFile("source", 30, 30, Bitmap.CompressFormat.JPEG)
            val fileStore = store(operations = TestFileOperations(failOpen = true))

            val result = fileStore.importImage(Uri.fromFile(source))

            assertTrue(result.isFailure)
            assertFalse(
                result
                    .exceptionOrNull()
                    ?.message
                    .orEmpty()
                    .contains(application.filesDir.path),
            )
            assertTrue(imagesDirectory.listFiles().isNullOrEmpty())
            source.delete()
        }

    @Test
    fun `partial output failure leaves neither temporary nor final output`() =
        runTest {
            val source = createBitmapFile("source", 30, 30, Bitmap.CompressFormat.JPEG)
            val fileStore = store(operations = TestFileOperations(partialWriteFailure = true))

            assertTrue(fileStore.importImage(Uri.fromFile(source)).isFailure)
            assertTrue(imagesDirectory.listFiles().isNullOrEmpty())
            source.delete()
        }

    @Test
    fun `encoding failure removes temporary output`() =
        runTest {
            val source = createBitmapFile("source", 30, 30, Bitmap.CompressFormat.JPEG)
            val fileStore = store(encoder = ImageEncoder { _, _, _, _ -> false })

            assertTrue(fileStore.importImage(Uri.fromFile(source)).isFailure)
            assertTrue(imagesDirectory.listFiles().isNullOrEmpty())
            source.delete()
        }

    @Test
    fun `atomic move failure removes temporary output and preserves target`() =
        runTest {
            val source = createBitmapFile("source", 30, 30, Bitmap.CompressFormat.JPEG)
            val existing = File(imagesDirectory, "attachment-test-0.jpg").apply { writeBytes(byteArrayOf(9)) }
            val fileStore = store(operations = TestFileOperations(failMove = true))

            assertTrue(fileStore.importImage(Uri.fromFile(source)).isFailure)
            assertTrue(existing.exists())
            assertEquals(listOf(existing.name), imagesDirectory.listFiles()!!.map(File::getName))
            source.delete()
        }

    @Test
    fun `existing target is not overwritten`() =
        runTest {
            val source = createBitmapFile("source", 30, 30, Bitmap.CompressFormat.JPEG)
            val existing = File(imagesDirectory, "attachment-test-0.jpg").apply { writeBytes(byteArrayOf(9, 8)) }

            val result = store().importImage(Uri.fromFile(source))

            assertTrue(result.isFailure)
            assertEquals(listOf<Byte>(9, 8), existing.readBytes().toList())
            source.delete()
        }

    @Test
    fun `failed temporary cleanup preserves primary failure and stale temporary is later cleaned`() =
        runTest {
            val source = createBitmapFile("source", 30, 30, Bitmap.CompressFormat.JPEG)
            val operations = TestFileOperations(failMove = true, failTemporaryDelete = true)
            val now = 2 * DAY_MILLIS
            val fileStore = store(operations = operations, now = { now })

            val result = fileStore.importImage(Uri.fromFile(source))
            val temporary = imagesDirectory.listFiles()!!.single { it.name.endsWith(".tmp") }
            temporary.setLastModified(0L)
            val cleanup = fileStore.cleanupOrphans(emptySet())

            assertTrue(result.isFailure)
            assertEquals("Unable to import image", result.exceptionOrNull()?.message)
            assertEquals(0, cleanup.deletedCount)
            assertEquals(1, cleanup.failedCount)
            assertTrue(temporary.exists())
            source.delete()
        }

    @Test
    fun `path resolution rejects traversal absolute blank and directories`() =
        runTest {
            val fileStore = store()

            assertNull(fileStore.fileFor("../outside.jpg"))
            assertNull(fileStore.fileFor("images/../../outside.jpg"))
            assertNull(fileStore.fileFor(".."))
            assertNull(fileStore.fileFor(""))
            assertNull(fileStore.fileFor("   "))
            assertNull(fileStore.fileFor("images/"))
            assertNull(fileStore.fileFor(File(application.filesDir, "outside.jpg").absolutePath))
            assertNull(fileStore.fileFor("images\\outside.jpg"))
            assertTrue(fileStore.fileFor("images/valid.jpg")!!.path.startsWith(imagesDirectory.canonicalPath))
            assertTrue(fileStore.delete("../outside.jpg").isFailure)
            assertFalse(fileStore.exists("images/../../outside.jpg"))
        }

    @Test
    fun `delete is idempotent for valid missing files and cannot escape managed directory`() =
        runTest {
            val outside = File(application.cacheDir, "outside.jpg").apply { writeBytes(byteArrayOf(1)) }
            val fileStore = store()

            assertTrue(fileStore.delete("images/missing.jpg").isSuccess)
            assertTrue(fileStore.delete(outside.absolutePath).isFailure)
            assertTrue(outside.exists())
            outside.delete()
        }

    @Test
    fun `orphan cleanup preserves referenced and fresh temporary files and removes stale files`() =
        runTest {
            val referenced = File(imagesDirectory, "referenced.jpg").apply { writeBytes(byteArrayOf(1)) }
            val orphan = File(imagesDirectory, "orphan.jpg").apply { writeBytes(byteArrayOf(1)) }
            val freshTemp =
                File(imagesDirectory, "fresh.jpg.tmp").apply {
                    writeBytes(byteArrayOf(1))
                    setLastModified(
                        2 * DAY_MILLIS,
                    )
                }
            val staleTemp =
                File(imagesDirectory, "stale.jpg.tmp").apply {
                    writeBytes(byteArrayOf(1))
                    setLastModified(0L)
                }
            val fileStore = store(now = { 2 * DAY_MILLIS })

            val first = fileStore.cleanupOrphans(setOf("images/${referenced.name}", "../outside.jpg"))
            val second = fileStore.cleanupOrphans(setOf("images/${referenced.name}"))

            assertTrue(referenced.exists())
            assertFalse(orphan.exists())
            assertTrue(freshTemp.exists())
            assertFalse(staleTemp.exists())
            assertEquals(2, first.deletedCount)
            assertEquals(0, first.failedCount)
            assertEquals(0, second.deletedCount)
            assertEquals(0, second.failedCount)
        }

    @Test
    fun `orphan cleanup continues after one deletion failure`() =
        runTest {
            val failed = File(imagesDirectory, "failed.jpg").apply { writeBytes(byteArrayOf(1)) }
            val deleted = File(imagesDirectory, "deleted.jpg").apply { writeBytes(byteArrayOf(1)) }
            val fileStore = store(operations = TestFileOperations(failDeleteName = failed.name))

            val cleanup = fileStore.cleanupOrphans(emptySet())

            assertTrue(failed.exists())
            assertFalse(deleted.exists())
            assertEquals(1, cleanup.deletedCount)
            assertEquals(1, cleanup.failedCount)
        }

    private fun store(
        operations: AttachmentFileOperations = DefaultAttachmentFileOperations,
        encoder: ImageEncoder = BitmapImageEncoder,
        now: () -> Long = System::currentTimeMillis,
    ): AndroidAttachmentFileStore =
        AndroidAttachmentFileStore(
            context = application,
            idGenerator = SequenceIdGenerator(),
            ioDispatcher = Dispatchers.Unconfined,
            config = ImageImportConfig(maxDimension = 100, quality = 85),
            testDependencies =
                AttachmentFileStoreTestDependencies(
                    fileOperations = operations,
                    imageEncoder = encoder,
                    currentTimeMillis = now,
                ),
        )

    private fun createBitmapFile(
        prefix: String,
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
        transparent: Boolean = false,
    ): File {
        val file = File(application.cacheDir, "$prefix-${System.nanoTime()}")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (transparent) Color.TRANSPARENT else Color.BLUE)
        FileOutputStream(file).use { output -> check(bitmap.compress(format, 100, output)) }
        bitmap.recycle()
        return file
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}

private fun runTest(block: suspend () -> Unit) {
    runBlocking { block() }
}

private class SequenceIdGenerator : IdGenerator {
    private var next = 0

    override fun generate(): String = "attachment-test-${next++}"
}

private class TestFileOperations(
    private val failOpen: Boolean = false,
    private val partialWriteFailure: Boolean = false,
    private val failMove: Boolean = false,
    private val failTemporaryDelete: Boolean = false,
    private val failDeleteName: String? = null,
) : AttachmentFileOperations {
    override fun openOutputStream(file: File): OutputStream {
        if (failOpen) throw IOException("write failure")
        val output = FileOutputStream(file)
        return if (partialWriteFailure) PartiallyFailingOutputStream(output) else output
    }

    override fun moveAtomically(
        source: File,
        target: File,
    ): Boolean = !failMove && source.renameTo(target)

    override fun delete(file: File): Boolean =
        when {
            file.name.endsWith(".tmp") && failTemporaryDelete -> false
            file.name == failDeleteName -> false
            else -> file.delete()
        }
}

private class PartiallyFailingOutputStream(
    private val delegate: OutputStream,
) : OutputStream() {
    private var hasWritten = false

    override fun write(value: Int) {
        if (hasWritten) throw IOException("write failure")
        delegate.write(value)
        hasWritten = true
    }

    override fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ) {
        if (length > 0) {
            delegate.write(buffer, offset, 1)
            hasWritten = true
        }
        throw IOException("write failure")
    }

    override fun flush() = delegate.flush()

    override fun close() = delegate.close()
}

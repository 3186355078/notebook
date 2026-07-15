package com.worklogai.app.core.backup

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.worklogai.app.core.attachment.AttachmentFileStore
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.datastore.AiSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

internal class BackupArchiveWriter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dataGateway: BackupDataGateway,
        private val attachmentFileStore: AttachmentFileStore,
        private val settingsRepository: AiSettingsRepository,
        private val timeProvider: TimeProvider,
    ) {
        suspend fun createBackup(destination: OutputStream): BackupOperationResult<BackupCreationResult> =
            when (val payload = loadPayload()) {
                is BackupOperationResult.Failure -> payload
                is BackupOperationResult.Success -> writeBackup(payload.value, destination)
            }

        private suspend fun loadPayload(): BackupOperationResult<BackupPayload> =
            dataGateway.snapshot().fold(
                onSuccess = { snapshot ->
                    runCatching { settingsRepository.getSettings() }.fold(
                        onSuccess = { settings -> BackupOperationResult.Success(snapshot.toPayload(settings)) },
                        onFailure = { failure("无法读取设置") },
                    )
                },
                onFailure = { failure("无法读取本地数据") },
            )

        private fun writeBackup(
            payload: BackupPayload,
            destination: OutputStream,
        ): BackupOperationResult<BackupCreationResult> {
            val archive = createTemporaryFile("backup", BackupArchiveContract.ZIP_EXTENSION)
            return try {
                val warnings = writeArchive(archive, payload)
                archive.inputStream().use { input -> input.copyTo(destination, BackupArchiveContract.COPY_BUFFER_SIZE) }
                destination.flush()
                BackupOperationResult.Success(BackupCreationResult(warnings))
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                failure("创建备份失败")
            } catch (_: IllegalStateException) {
                failure("创建备份失败")
            } finally {
                archive.delete()
            }
        }

        private fun BackupDatabaseSnapshot.toPayload(settings: AiSettings): BackupPayload {
            val includedPaths =
                attachments.associate { attachment ->
                    attachment.localPath to (attachmentFileStore.fileFor(attachment.localPath)?.isFile == true)
                }
            return BackupPayload(
                entries.map { it.toBackup() },
                blocks.map { it.toBackup() },
                attachments.map { it.toBackup(includedPaths[it.localPath] == true) },
                summaries.map { it.toBackup() },
                settings.toBackup(),
            )
        }

        private fun writeArchive(
            file: File,
            payload: BackupPayload,
        ): Int {
            val stablePayload =
                payload.copy(
                    attachments =
                        payload.attachments.map { attachment ->
                            attachment.copy(
                                fileIncluded =
                                    attachmentFileStore.fileFor(attachment.localPath)?.isFile == true,
                            )
                        },
                )
            var warningCount = stablePayload.attachments.count { !it.fileIncluded }
            val files = mutableListOf<BackupFileManifest>()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
                writeJson(zip, BackupArchiveContract.ENTRIES_FILE, stablePayload.entries, files)
                writeJson(zip, BackupArchiveContract.BLOCKS_FILE, stablePayload.blocks, files)
                writeJson(zip, BackupArchiveContract.ATTACHMENTS_FILE, stablePayload.attachments, files)
                writeJson(zip, BackupArchiveContract.SUMMARIES_FILE, stablePayload.summaries, files)
                writeJson(zip, BackupArchiveContract.SETTINGS_FILE, stablePayload.settings, files)
                stablePayload.attachments.filter(BackupAttachment::fileIncluded).forEach { attachment ->
                    val source = attachmentFileStore.fileFor(attachment.localPath)
                    if (source == null || !source.isFile) {
                        warningCount++
                    } else {
                        writeFile(zip, BackupArchivePaths.attachmentEntryPath(attachment.localPath), source, files)
                    }
                }
                val manifest =
                    BackupManifest(
                        formatName = BACKUP_FORMAT_NAME,
                        formatVersion = BACKUP_FORMAT_VERSION,
                        appId = context.packageName,
                        appVersionName = packageInfo().versionName.orEmpty(),
                        appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo()),
                        createdAt = timeProvider.now().toString(),
                        databaseVersion = com.worklogai.app.core.database.WorkLogDatabase.VERSION,
                        entryCount = stablePayload.entries.size,
                        blockCount = stablePayload.blocks.size,
                        attachmentCount = stablePayload.attachments.size,
                        summaryCount = stablePayload.summaries.size,
                        files = files.sortedBy(BackupFileManifest::path),
                    )
                writeEntry(
                    zip,
                    BackupArchiveContract.MANIFEST_FILE,
                    backupJson.encodeToString(manifest).encodeToByteArray(),
                )
            }
            return warningCount
        }

        private fun packageInfo() = context.packageManager.getPackageInfo(context.packageName, 0)

        private inline fun <reified T> writeJson(
            zip: ZipOutputStream,
            path: String,
            value: T,
            manifest: MutableList<BackupFileManifest>,
        ) {
            val bytes = backupJson.encodeToString(value).encodeToByteArray()
            writeEntry(zip, path, bytes)
            manifest += BackupFileManifest(path, bytes.size.toLong(), BackupArchiveStreams.sha256(bytes))
        }

        private fun writeFile(
            zip: ZipOutputStream,
            path: String,
            source: File,
            manifest: MutableList<BackupFileManifest>,
        ) {
            val digest = MessageDigest.getInstance(BackupArchiveContract.SHA_256)
            zip.putNextEntry(ZipEntry(path))
            var size = 0L
            BufferedInputStream(FileInputStream(source)).use { input ->
                val buffer = ByteArray(BackupArchiveContract.COPY_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    zip.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    size += count
                }
            }
            zip.closeEntry()
            manifest += BackupFileManifest(path, size, BackupArchiveStreams.hex(digest.digest()))
        }

        private fun writeEntry(
            zip: ZipOutputStream,
            path: String,
            bytes: ByteArray,
        ) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }

        private fun createTemporaryFile(
            prefix: String,
            suffix: String,
        ): File =
            File.createTempFile(
                prefix,
                suffix,
                File(context.cacheDir, BackupArchiveContract.BACKUP_CACHE_DIRECTORY).apply { mkdirs() },
            )
    }

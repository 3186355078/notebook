package com.worklogai.app.core.backup

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject

/** Internal file-system boundary used to make restore compensation deterministic in tests. */
internal interface RestoreDirectoryOperations {
    fun createStageDirectory(cacheDirectory: File): File

    fun moveDirectory(
        source: File,
        target: File,
    )

    fun deleteRecursively(directory: File): Boolean
}

internal class DefaultRestoreDirectoryOperations
    @Inject
    constructor() : RestoreDirectoryOperations {
        override fun createStageDirectory(cacheDirectory: File): File =
            File(cacheDirectory, "restore-stage-${UUID.randomUUID()}").apply {
                check(mkdirs())
            }

        override fun moveDirectory(
            source: File,
            target: File,
        ) {
            target.parentFile?.mkdirs()
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }

        override fun deleteRecursively(directory: File): Boolean = !directory.exists() || directory.deleteRecursively()
    }

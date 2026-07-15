package com.worklogai.app.core.database.hash

import com.worklogai.app.core.model.Attachment
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import com.worklogai.app.core.model.WorkEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SummarySourceHasherTest {
    private val hasher = Sha256SummarySourceHasher()
    private val createdAt = Instant.parse("2026-07-12T08:00:00Z")

    @Test
    fun `returns same hash for equivalent content regardless of query order and updated time`() {
        val original = entry(updatedAt = createdAt, attachmentPath = "images/a.jpg")
        val reorderedQuery =
            entry(
                updatedAt = createdAt.plusSeconds(60),
                attachmentPath = "other-directory/a.jpg",
            ).copy(blocks = original.blocks.reversed())

        assertEquals(hasher.hash(listOf(original)), hasher.hash(listOf(reorderedQuery)))
    }

    @Test
    fun `changes hash when text table or image caption changes`() {
        val original = entry(updatedAt = createdAt, attachmentPath = "images/a.jpg")
        val changedText =
            original.copy(
                blocks =
                    original.blocks.map { block ->
                        if (block is ContentBlock.Text) block.copy(content = "修订后的文字") else block
                    },
            )
        val changedTable =
            original.copy(
                blocks =
                    original.blocks.map { block ->
                        if (block is ContentBlock.Table) {
                            block.copy(content = block.content.copy(title = "修订表格"))
                        } else {
                            block
                        }
                    },
            )
        val changedCaption =
            original.copy(
                blocks =
                    original.blocks.map { block ->
                        if (block is ContentBlock.Image) {
                            block.copy(attachments = block.attachments.map { it.copy(caption = "新截图说明") })
                        } else {
                            block
                        }
                    },
            )

        assertNotEquals(hasher.hash(listOf(original)), hasher.hash(listOf(changedText)))
        assertNotEquals(hasher.hash(listOf(original)), hasher.hash(listOf(changedTable)))
        assertNotEquals(hasher.hash(listOf(original)), hasher.hash(listOf(changedCaption)))
    }

    private fun entry(
        updatedAt: Instant,
        attachmentPath: String,
    ): WorkEntry =
        WorkEntry(
            id = "entry-id",
            entryDate = LocalDate.of(2026, 7, 7),
            title = "登录模块",
            allowAiProcessing = true,
            isDeleted = false,
            createdAt = createdAt,
            updatedAt = updatedAt,
            blocks =
                listOf(
                    ContentBlock.Text(
                        id = "text-id",
                        entryId = "entry-id",
                        order = 0,
                        content = "完成登录接口",
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    ),
                    ContentBlock.Table(
                        id = "table-id",
                        entryId = "entry-id",
                        order = 1,
                        content =
                            TableContent(
                                title = "测试结果",
                                columns = listOf(TableColumn(id = "status", name = "状态")),
                                rows = listOf(TableRow(id = "row-id", cells = mapOf("status" to "通过"))),
                            ),
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    ),
                    ContentBlock.Image(
                        id = "image-id",
                        entryId = "entry-id",
                        order = 2,
                        attachments =
                            listOf(
                                Attachment(
                                    id = "attachment-id",
                                    blockId = "image-id",
                                    localPath = attachmentPath,
                                    mimeType = "image/jpeg",
                                    fileSize = 128,
                                    width = 100,
                                    height = 50,
                                    caption = "登录页面截图",
                                    createdAt = createdAt,
                                ),
                            ),
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    ),
                ),
        )
}

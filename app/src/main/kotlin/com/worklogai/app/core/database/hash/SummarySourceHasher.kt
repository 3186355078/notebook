package com.worklogai.app.core.database.hash

import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.WorkEntry
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject

fun interface SummarySourceHasher {
    fun hash(entries: List<WorkEntry>): String
}

class Sha256SummarySourceHasher
    @Inject
    constructor() : SummarySourceHasher {
        override fun hash(entries: List<WorkEntry>): String {
            val canonical =
                buildString {
                    entries
                        .sortedBy(WorkEntry::entryDate)
                        .forEach { entry ->
                            appendToken("entry")
                            appendToken(entry.entryDate.toString())
                            appendToken(entry.title)
                            appendToken(entry.allowAiProcessing.toString())
                            entry.blocks
                                .sortedWith(
                                    compareBy<ContentBlock>(ContentBlock::order)
                                        .thenBy(ContentBlock::createdAt)
                                        .thenBy(ContentBlock::id),
                                ).forEach { block -> appendBlock(block) }
                        }
                }
            return MessageDigest
                .getInstance("SHA-256")
                .digest(canonical.toByteArray(StandardCharsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        }

        private fun StringBuilder.appendBlock(block: ContentBlock) {
            appendToken("block")
            appendToken(block.type.name)
            appendToken(block.order.toString())
            when (block) {
                is ContentBlock.Image -> {
                    block.attachments
                        .sortedBy { it.id }
                        .forEach { attachment -> appendToken(attachment.caption) }
                }

                is ContentBlock.Table -> appendTable(block.content)
                is ContentBlock.Text -> appendToken(block.content)
            }
        }

        private fun StringBuilder.appendTable(content: TableContent) {
            appendToken("table")
            appendToken(content.title)
            content.columns.forEach { column -> appendToken(column.name) }
            content.rows.forEach { row ->
                content.columns.forEach { column -> appendToken(row.cells[column.id]) }
            }
        }

        private fun StringBuilder.appendToken(value: String?) {
            if (value == null) {
                append("-1:")
            } else {
                append(value.length)
                append(':')
                append(value)
            }
        }
    }

package com.worklogai.app.core.designsystem.markdown

object MarkdownParser {
    private const val TAB_WIDTH = 4
    private const val LIST_INDENT_WIDTH = 2
    private const val MAX_LIST_DEPTH = 3
    private const val SINGLE_CHARACTER = 1
    private const val LIST_BODY_GROUP = 4

    private val headingPattern = Regex("^(#{1,4})[\\t ]+(.+)$")
    private val quotePattern = Regex("^[\\t ]{0,3}>[\\t ]?(.*)$")
    private val listPattern = Regex("^([\\t ]*)([-+*]|(\\d+)[.)])[\\t ]+(.+)$")
    private val taskPattern = Regex("^\\[([xX ])][\\t ]+(.+)$")
    private val dividerPattern =
        Regex(
            "^[\\t ]{0,3}(?:" +
                "((?:\\*[\\t ]*){3,})|" +
                "((?:-[\\t ]*){3,})|" +
                "((?:_[\\t ]*){3,}))" +
                "[\\t ]*$",
        )

    fun parse(markdown: String): MarkdownDocument {
        if (markdown.isEmpty()) return MarkdownDocument(emptyList())
        val cursor = LineCursor(markdown.toLines())
        val blocks = mutableListOf<MarkdownBlock>()
        while (cursor.hasCurrent) {
            cursor.skipBlankLines()
            if (cursor.hasCurrent) blocks += parseBlock(cursor)
        }
        return MarkdownDocument(blocks)
    }

    private fun parseBlock(cursor: LineCursor): MarkdownBlock {
        if (dividerPattern.matches(cursor.current)) {
            cursor.advance()
            return MarkdownBlock.Divider
        }
        return when {
            isFence(cursor.current) -> parseCodeBlock(cursor)
            headingPattern.matches(cursor.current) -> parseHeading(cursor)
            quotePattern.matches(cursor.current) -> parseQuote(cursor)
            listPattern.matches(cursor.current) -> parseList(cursor)
            else -> parseParagraph(cursor)
        }
    }

    private fun parseCodeBlock(cursor: LineCursor): MarkdownBlock.CodeBlock {
        val language =
            cursor.current
                .trimStart()
                .removePrefix("```")
                .trim()
                .ifBlank { null }
        cursor.advance()
        val code = cursor.consumeUntil(::isFence)
        if (cursor.hasCurrent) cursor.advance()
        return MarkdownBlock.CodeBlock(language, code.joinToString("\n"))
    }

    private fun parseHeading(cursor: LineCursor): MarkdownBlock.Heading {
        val match = requireNotNull(headingPattern.matchEntire(cursor.current))
        cursor.advance()
        return MarkdownBlock.Heading(
            level = match.groupValues[1].length,
            content = parseInline(match.groupValues[2].trimEnd()),
        )
    }

    private fun parseQuote(cursor: LineCursor): MarkdownBlock.Quote {
        val lines = mutableListOf<String>()
        while (cursor.hasCurrent) {
            val match = quotePattern.matchEntire(cursor.current) ?: break
            lines += match.groupValues[1]
            cursor.advance()
        }
        return MarkdownBlock.Quote(parseInline(lines.joinToString(" ").trim()))
    }

    private fun parseList(cursor: LineCursor): MarkdownBlock.ListBlock {
        val items = mutableListOf<MarkdownListItem>()
        while (cursor.hasCurrent) {
            val match = listPattern.matchEntire(cursor.current) ?: break
            items += parseListItem(match)
            cursor.advance()
        }
        return MarkdownBlock.ListBlock(items)
    }

    private fun parseListItem(match: MatchResult): MarkdownListItem {
        val indentation =
            match.groupValues[1]
                .replace("\t", " ".repeat(TAB_WIDTH))
                .length
        val marker = match.groupValues[2]
        val rawBody = match.groupValues[LIST_BODY_GROUP]
        val task = taskPattern.matchEntire(rawBody)
        val body = task?.groupValues?.get(2) ?: rawBody
        return MarkdownListItem(
            depth = (indentation / LIST_INDENT_WIDTH).coerceIn(0, MAX_LIST_DEPTH),
            ordinal = marker.toOrdinalOrNull(),
            checked = task?.groupValues?.get(1)?.equals("x", ignoreCase = true),
            content = parseInline(body),
        )
    }

    private fun parseParagraph(cursor: LineCursor): MarkdownBlock.Paragraph {
        val lines = mutableListOf<String>()
        while (cursor.hasCurrent && cursor.current.isNotBlank() && !startsBlock(cursor.current)) {
            lines += cursor.current.trim()
            cursor.advance()
        }
        return MarkdownBlock.Paragraph(parseInline(lines.joinToString(" ")))
    }

    private fun startsBlock(line: String): Boolean =
        isFence(line) ||
            headingPattern.matches(line) ||
            dividerPattern.matches(line) ||
            quotePattern.matches(line) ||
            listPattern.matches(line)

    internal fun parseInline(text: String): List<MarkdownInline> = InlineParser(text).parse()

    private class InlineParser(
        private val source: String,
    ) {
        fun parse(): List<MarkdownInline> = parseRange(0, source.length)

        private fun parseRange(
            start: Int,
            end: Int,
        ): List<MarkdownInline> {
            val output = mutableListOf<MarkdownInline>()
            var cursor = start
            var plainStart = start

            fun flushPlain(until: Int) {
                if (until > plainStart) output.appendText(source.substring(plainStart, until))
            }

            while (cursor < end) {
                val escaped = source[cursor] == '\\' && cursor + 1 < end
                val parsed = if (escaped) null else parseSpecial(cursor, end)
                when {
                    escaped -> {
                        flushPlain(cursor)
                        output.appendText(source[cursor + 1].toString())
                        cursor += 2
                        plainStart = cursor
                    }
                    parsed != null -> {
                        flushPlain(cursor)
                        output += parsed.inline
                        cursor = parsed.nextIndex
                        plainStart = cursor
                    }
                    else -> cursor++
                }
            }
            flushPlain(end)
            return output
        }

        private fun parseSpecial(
            index: Int,
            end: Int,
        ): ParsedInline? =
            parseImage(index, end)
                ?: parseLink(index, end)
                ?: delimiters.firstNotNullOfOrNull { spec -> parseDelimited(index, end, spec) }

        private fun parseImage(
            index: Int,
            end: Int,
        ): ParsedInline? =
            if (source.startsWith("![", index)) {
                parseBracketTarget(index + 2, end)?.let { target ->
                    ParsedInline(
                        MarkdownInline.ImageAlternative(target.label),
                        target.nextIndex,
                    )
                }
            } else {
                null
            }

        private fun parseLink(
            index: Int,
            end: Int,
        ): ParsedInline? =
            if (source.getOrNull(index) == '[' && source.getOrNull(index - 1) != '!') {
                parseBracketTarget(index + 1, end)?.let { target ->
                    val link =
                        MarkdownInline.Link(
                            children = parseRange(index + 1, target.labelEnd),
                            destination = target.destination,
                        )
                    ParsedInline(link, target.nextIndex)
                }
            } else {
                null
            }

        private fun parseBracketTarget(
            labelStart: Int,
            end: Int,
        ): BracketTarget? {
            val labelEnd = source.indexOf(']', labelStart)
            val hasDestinationStart = labelEnd in labelStart until end && source.getOrNull(labelEnd + 1) == '('
            val destinationEnd = if (hasDestinationStart) findDestinationEnd(labelEnd + 2, end) else -1
            return if (destinationEnd in (labelEnd + 2) until end) {
                BracketTarget(
                    label = source.substring(labelStart, labelEnd),
                    labelEnd = labelEnd,
                    destination = source.substring(labelEnd + 2, destinationEnd),
                    nextIndex = destinationEnd + 1,
                )
            } else {
                null
            }
        }

        private fun findDestinationEnd(
            start: Int,
            end: Int,
        ): Int {
            var cursor = start
            var nestedParentheses = 0
            while (cursor < end) {
                when {
                    source[cursor] == '\\' && cursor + 1 < end -> cursor++
                    source[cursor] == '(' -> nestedParentheses++
                    source[cursor] == ')' && nestedParentheses > 0 -> nestedParentheses--
                    source[cursor] == ')' -> return cursor
                }
                cursor++
            }
            return -1
        }

        private fun parseDelimited(
            index: Int,
            end: Int,
            spec: DelimiterSpec,
        ): ParsedInline? {
            val startsDelimiter = source.startsWith(spec.token, index)
            val startsLongerDelimiter =
                spec.token.length == SINGLE_CHARACTER && source.startsWith(spec.token.repeat(2), index)
            if (!startsDelimiter || startsLongerDelimiter) return null
            val contentStart = index + spec.token.length
            val contentEnd = source.indexOf(spec.token, contentStart)
            return if (contentEnd in (contentStart + 1) until end) {
                val children = parseRange(contentStart, contentEnd)
                ParsedInline(
                    inline = spec.kind.toInline(source.substring(contentStart, contentEnd), children),
                    nextIndex = contentEnd + spec.token.length,
                )
            } else {
                null
            }
        }

        private data class ParsedInline(
            val inline: MarkdownInline,
            val nextIndex: Int,
        )

        private data class BracketTarget(
            val label: String,
            val labelEnd: Int,
            val destination: String,
            val nextIndex: Int,
        )
    }

    private class LineCursor(
        private val lines: List<String>,
    ) {
        private var index = 0
        val hasCurrent: Boolean get() = index < lines.size
        val current: String get() = lines[index]

        fun advance() {
            index++
        }

        fun skipBlankLines() {
            while (hasCurrent && current.isBlank()) advance()
        }

        fun consumeUntil(predicate: (String) -> Boolean): List<String> {
            val consumed = mutableListOf<String>()
            while (hasCurrent && !predicate(current)) {
                consumed += current
                advance()
            }
            return consumed
        }
    }

    private enum class InlineKind {
        CODE,
        STRONG,
        EMPHASIS,
        STRONG_EMPHASIS,
        ;

        fun toInline(
            raw: String,
            children: List<MarkdownInline>,
        ): MarkdownInline =
            when (this) {
                CODE -> MarkdownInline.InlineCode(raw)
                STRONG -> MarkdownInline.Strong(children)
                EMPHASIS -> MarkdownInline.Emphasis(children)
                STRONG_EMPHASIS ->
                    MarkdownInline.Strong(
                        listOf(MarkdownInline.Emphasis(children)),
                    )
            }
    }

    private data class DelimiterSpec(
        val token: String,
        val kind: InlineKind,
    )

    private val delimiters =
        listOf(
            DelimiterSpec("`", InlineKind.CODE),
            DelimiterSpec("***", InlineKind.STRONG_EMPHASIS),
            DelimiterSpec("___", InlineKind.STRONG_EMPHASIS),
            DelimiterSpec("**", InlineKind.STRONG),
            DelimiterSpec("__", InlineKind.STRONG),
            DelimiterSpec("*", InlineKind.EMPHASIS),
            DelimiterSpec("_", InlineKind.EMPHASIS),
        )
}

private fun isFence(line: String): Boolean = line.trimStart().startsWith("```")

private fun String.toLines(): List<String> =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')

private fun String.toOrdinalOrNull(): Int? =
    takeIf { it.firstOrNull()?.isDigit() == true }
        ?.takeWhile(Char::isDigit)
        ?.toIntOrNull()

private fun MutableList<MarkdownInline>.appendText(value: String) {
    if (value.isEmpty()) return
    val previous = lastOrNull()
    if (previous is MarkdownInline.Text) {
        this[lastIndex] = MarkdownInline.Text(previous.value + value)
    } else {
        add(MarkdownInline.Text(value))
    }
}

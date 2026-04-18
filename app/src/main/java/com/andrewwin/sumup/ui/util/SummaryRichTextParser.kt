package com.andrewwin.sumup.ui.util

import com.andrewwin.sumup.domain.support.DebugTrace
import com.andrewwin.sumup.domain.support.SummarySourceMeta

data class SummarySourceLinkUi(
    val name: String,
    val url: String
)

sealed interface SummaryBlockUi {
    data class Section(
        val body: String,
        val source: SummarySourceLinkUi?
    ) : SummaryBlockUi

    data class Theme(
        val heading: String,
        val items: List<ThemeItem>
    ) : SummaryBlockUi

    data class PlainList(
        val items: List<ThemeItem>
    ) : SummaryBlockUi
}

data class ThemeItem(
    val marker: String,
    val text: String,
    val source: SummarySourceLinkUi?
)

private const val ThemeItemMarker = "—"
private val SourceMetaInlineRegex = Regex("${Regex.escape(SummarySourceMeta.PREFIX)}[^\\n]*")

fun parseSummaryBlocks(raw: String): List<SummaryBlockUi> {
    val normalizedRaw = raw.replace(Regex("\\s*${Regex.escape(SummarySourceMeta.PREFIX)}"), "\n${SummarySourceMeta.PREFIX}")
    return normalizedRaw
        .split(Regex("\\n\\s*\\n"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { block -> parseThemeBlock(block) ?: parsePlainListBlock(block) ?: parseSectionBlock(block) }
        .also { blocks ->
            val themeCount = blocks.count { it is SummaryBlockUi.Theme }
            val sectionCount = blocks.count { it is SummaryBlockUi.Section }
            val listCount = blocks.count { it is SummaryBlockUi.PlainList }
            DebugTrace.d(
                "summary_parser",
                "parseSummaryBlocks themes=$themeCount sections=$sectionCount lists=$listCount preview=${DebugTrace.preview(raw, 280)}"
            )
        }
}

fun cleanSummaryTextForSharing(raw: String): String {
    val normalizedRaw = raw.replace(Regex("\\s*${Regex.escape(SummarySourceMeta.PREFIX)}"), "\n${SummarySourceMeta.PREFIX}")
    return normalizedRaw
        .replace(SourceMetaInlineRegex, "")
        .lines()
        .filterNot { it.trim().startsWith(SummarySourceMeta.PREFIX) }
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

fun normalizeSummaryUrlForWebView(url: String): String {
    val trimmed = url.trim()
    if (!trimmed.contains("t.me/")) return trimmed
    val marker = "t.me/"
    val idx = trimmed.indexOf(marker)
    if (idx == -1) return trimmed
    val prefix = trimmed.substring(0, idx + marker.length)
    var path = trimmed.substring(idx + marker.length).trimStart('/')
    if (path.startsWith("s/")) return "$prefix$path"
    if (path.startsWith("c/")) return "$prefix$path"
    val segments = path.split("/").filter { it.isNotBlank() }
    if (segments.size >= 2) {
        path = "s/${segments[0]}/${segments[1]}"
        return "$prefix$path"
    }
    if (segments.size == 1) {
        return "${prefix}s/${segments[0]}"
    }
    return trimmed
}

private fun parseThemeBlock(block: String): SummaryBlockUi.Theme? {
    val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    if (lines.size < 2) return null
    val heading = lines.first().trim()
    if (heading.startsWith(ThemeItemMarker) || heading.startsWith(SummarySourceMeta.PREFIX)) return null

    val items = mutableListOf<ThemeItem>()
    var index = 1
    while (index < lines.size) {
        val line = lines[index].trim()
        if (!line.startsWith(ThemeItemMarker)) return null
        val text = line.removePrefix(ThemeItemMarker).trim()
        if (text.isBlank()) return null
        var source: SummarySourceLinkUi? = null
        if (index + 1 < lines.size) {
            source = parseSourceMeta(lines[index + 1].trim())
            if (source != null) index++
        }
        items += ThemeItem(marker = ThemeItemMarker, text = text, source = source)
        index++
    }

    return items.takeIf { it.isNotEmpty() }?.let {
        SummaryBlockUi.Theme(
            heading = heading,
            items = it
        )
    }
}

private fun parseSectionBlock(block: String): SummaryBlockUi.Section? {
    val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) return null
    val source = lines.lastOrNull()?.let { parseSourceMeta(it.trim()) }
    val bodyLines = if (source != null) lines.dropLast(1) else lines
    val body = bodyLines.joinToString("\n").replace(SourceMetaInlineRegex, "").trim()
    if (body.isBlank()) return null
    return SummaryBlockUi.Section(body = body, source = source)
}

private fun parsePlainListBlock(block: String): SummaryBlockUi.PlainList? {
    val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) return null
    if (!lines.first().trim().startsWith(ThemeItemMarker)) return null

    val items = mutableListOf<ThemeItem>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()
        if (!line.startsWith(ThemeItemMarker)) return null
        val text = line.removePrefix(ThemeItemMarker).trim()
        if (text.isBlank()) return null
        var source: SummarySourceLinkUi? = null
        if (index + 1 < lines.size) {
            source = parseSourceMeta(lines[index + 1].trim())
            if (source != null) index++
        }
        items += ThemeItem(marker = ThemeItemMarker, text = text, source = source)
        index++
    }

    return items.takeIf { it.isNotEmpty() }?.let { SummaryBlockUi.PlainList(it) }
}

private fun parseSourceMeta(line: String): SummarySourceLinkUi? {
    if (!line.startsWith(SummarySourceMeta.PREFIX)) return null
    val payload = line.removePrefix(SummarySourceMeta.PREFIX)
    val separator = payload.lastIndexOf('|')
    if (separator <= 0 || separator >= payload.lastIndex) return null
    val name = payload.substring(0, separator).trim()
    val url = payload.substring(separator + 1).trim()
    if (name.isBlank() || url.isBlank()) return null
    return SummarySourceLinkUi(name, url)
}

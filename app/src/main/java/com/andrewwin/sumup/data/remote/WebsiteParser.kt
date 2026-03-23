package com.andrewwin.sumup.data.remote

import android.util.Log
import com.andrewwin.sumup.data.local.entities.Article
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class WebsiteParser @Inject constructor() {

    fun parse(
        sourceId: Long,
        sourceUrl: String,
        html: String,
        titleSelector: String,
        postLinkSelector: String?,
        descriptionSelector: String?,
        dateSelector: String?
    ): List<Article> {
        if (html.isBlank()) {
            Log.d(TAG_WEBSITE_PARSER, "skip: sourceId=$sourceId, reason=blank_html")
            return emptyList()
        }
        val document = Jsoup.parse(html, sourceUrl)
        val normalizedSelector = normalizeCssSelector(titleSelector)
        if (normalizedSelector != titleSelector) {
            Log.d(
                TAG_WEBSITE_PARSER,
                "selector_normalized: sourceId=$sourceId, raw='$titleSelector', normalized='$normalizedSelector'"
            )
        }

        val titleElements = selectTitleElementsWithFallback(
            document = document,
            selector = normalizedSelector,
            sourceId = sourceId
        )
        Log.d(
            TAG_WEBSITE_PARSER,
            "selected_titles: sourceId=$sourceId, selector=$normalizedSelector, count=${titleElements.size}"
        )
        if (titleElements.isEmpty()) {
            val allAnchors = document.select("a")
            val classHistogram = allAnchors
                .map { it.className().trim() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(MAX_CLASS_HISTOGRAM_ITEMS)
                .joinToString(" | ") { "${it.key}:${it.value}" }
            val classContainsImTl = allAnchors
                .filter { it.className().contains("im", ignoreCase = true) || it.className().contains("tl", ignoreCase = true) }
                .take(MAX_ANCHOR_SAMPLES)
                .joinToString(" || ") { anchor ->
                    "class='${anchor.className()}' text='${anchor.text().take(50)}' href='${anchor.attr("href")}'"
                }
            val selectorTokens = normalizedSelector
                .split('.', '#', ' ', '>', '[', ']', ':')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val tokenHints = selectorTokens.joinToString(" | ") { token ->
                "token='$token' hits=${html.contains(token)}"
            }
            Log.d(
                TAG_WEBSITE_PARSER,
                "diagnostic_zero_match: sourceId=$sourceId, anchors=${allAnchors.size}, classHistogram=$classHistogram"
            )
            Log.d(
                TAG_WEBSITE_PARSER,
                "diagnostic_selector_tokens: sourceId=$sourceId, $tokenHints"
            )
            Log.d(
                TAG_WEBSITE_PARSER,
                "diagnostic_anchor_samples: sourceId=$sourceId, $classContainsImTl"
            )
            Log.d(TAG_WEBSITE_PARSER, "skip: sourceId=$sourceId, reason=no_title_elements")
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val baseUri = runCatching { URI(sourceUrl) }.getOrNull()

        val items = titleElements.mapIndexedNotNull { index, titleElement ->
            val title = titleElement.text().trim()
            if (title.isBlank()) {
                Log.d(TAG_WEBSITE_PARSER, "drop_item[$index]: sourceId=$sourceId, reason=blank_title")
                return@mapIndexedNotNull null
            }

            val itemRoot = titleElement.parent()
            val candidateRoots = buildCandidateRoots(titleElement, itemRoot)
            val postUrl = resolveUrl(
                baseUri = baseUri,
                relativeOrAbsolute = selectPostLink(titleElement, candidateRoots, postLinkSelector)
            )
            val description = selectOptionalText(candidateRoots, descriptionSelector)
            val fallbackPublishedAt = now - index * DEFAULT_ITEM_TIME_STEP_MS
            val publishedAt = parseDateToMillis(selectOptionalText(candidateRoots, dateSelector), fallbackPublishedAt)
            val articleUrl = buildArticleUrl(
                sourceUrl = sourceUrl,
                resolvedPostUrl = postUrl,
                title = title,
                index = index
            )
            val contentToSave = description ?: title
            Log.d(
                TAG_WEBSITE_PARSER,
                "item_content[$index]: sourceId=$sourceId, titleLen=${title.length}, descriptionLen=${description?.length ?: 0}, contentLen=${contentToSave.length}, contentPreview='${contentToSave.take(LOG_CONTENT_PREVIEW_LEN)}'"
            )
            Log.d(
                TAG_WEBSITE_PARSER,
                "item[$index]: sourceId=$sourceId, titleLen=${title.length}, resolvedUrl=$articleUrl, hasDescription=${!description.isNullOrBlank()}, publishedAt=$publishedAt"
            )

            Article(
                sourceId = sourceId,
                title = title,
                content = contentToSave,
                url = articleUrl,
                publishedAt = publishedAt
            )
        }
        Log.d(TAG_WEBSITE_PARSER, "result: sourceId=$sourceId, items=${items.size}")
        return items
    }

    private fun selectTitleElementsWithFallback(
        document: Document,
        selector: String,
        sourceId: Long
    ): List<Element> {
        val primary = runCatching { document.select(selector) }.getOrDefault(emptyList())
        if (primary.isNotEmpty()) return primary

        val fallbackSelectors = buildFallbackSelectors(selector)
        for (fallback in fallbackSelectors) {
            val matched = runCatching { document.select(fallback) }.getOrDefault(emptyList())
            Log.d(
                TAG_WEBSITE_PARSER,
                "selector_fallback: sourceId=$sourceId, fallback='$fallback', count=${matched.size}"
            )
            if (matched.isNotEmpty()) return matched
        }

        return emptyList()
    }

    private fun buildFallbackSelectors(selector: String): List<String> {
        val result = mutableListOf<String>()
        val tagClassRegex = Regex("^([a-zA-Z*][a-zA-Z0-9_-]*)\\.([a-zA-Z0-9_-]+)$")
        val match = tagClassRegex.matchEntire(selector)
        if (match != null) {
            val tag = match.groupValues[1]
            val className = match.groupValues[2]
            result.add("$tag[class~=\"$className\"]")
            result.add("$tag[class*=\"$className\"]")
            if (tag == "a") {
                result.add("a[href]")
                result.add("a")
            } else {
                result.add(tag)
            }
        } else if (selector.startsWith("a")) {
            result.add("a[href]")
            result.add("a")
        }
        return result.distinct()
    }

    private fun normalizeCssSelector(selector: String): String {
        var value = selector.trim().trim('"', '\'', '`')
        value = value.replace(Regex("\\s+"), " ")
        value = value.replace(Regex("([a-zA-Z0-9_*])\\s+\\."), "$1.")
        value = value.replace(Regex("\\s*([>+~])\\s*"), " $1 ")
        return value.trim()
    }

    private fun selectPostLink(
        titleElement: Element,
        candidateRoots: List<Element>,
        postLinkSelector: String?
    ): String? {
        if (!postLinkSelector.isNullOrBlank()) {
            val normalized = normalizeCssSelector(postLinkSelector)
            val candidates = listOf(normalized) + buildFallbackSelectors(normalized)
            candidates.forEach { selector ->
                candidateRoots.forEach { root ->
                    val explicit = root.selectFirst(selector)?.attr("href").orEmpty()
                    if (explicit.isNotBlank()) return explicit
                }
            }
        }

        val titleHref = titleElement.attr("href")
        if (titleHref.isNotBlank()) return titleHref
        return titleElement.selectFirst("a[href]")?.attr("href")
    }

    private fun selectOptionalText(candidateRoots: List<Element>, selector: String?): String? {
        if (selector.isNullOrBlank()) return null
        val normalized = normalizeCssSelector(selector)
        val candidates = listOf(normalized) + buildFallbackSelectors(normalized)
        candidates.forEach { css ->
            candidateRoots.forEach { root ->
                val value = root.selectFirst(css)?.text()?.trim().orEmpty()
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }

    private fun parseDateToMillis(rawDate: String?, fallbackMillis: Long): Long {
        if (rawDate.isNullOrBlank()) return fallbackMillis
        val trimmed = rawDate.trim()
        return runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(rawDate).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(rawDate).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(rawDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }.getOrNull()
            ?: parseWithRfc(rawDate)
            ?: parseWithDayMonthAndTime(trimmed)
            ?: parseWithTimeOnly(trimmed)
            ?: fallbackMillis
    }

    private fun parseWithRfc(rawDate: String): Long? {
        val normalized = rawDate.trim().replace("GMT", "+0000")
        return runCatching {
            val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
            ZonedDateTime.parse(normalized, formatter).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun resolveUrl(baseUri: URI?, relativeOrAbsolute: String?): String? {
        if (relativeOrAbsolute.isNullOrBlank()) return null
        val value = relativeOrAbsolute.trim()
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
            return value
        }
        val base = baseUri ?: return null
        return runCatching { base.resolve(value).toString() }.getOrNull()
    }

    private fun buildArticleUrl(
        sourceUrl: String,
        resolvedPostUrl: String?,
        title: String,
        index: Int
    ): String {
        val validPostUrl = resolvedPostUrl
            ?.trim()
            ?.takeUnless { it.startsWith("javascript:", ignoreCase = true) }
            ?.takeUnless { it == "#" }
            ?.takeUnless { it.isBlank() }
        if (validPostUrl != null) return validPostUrl
        val fallbackId = title.lowercase().hashCode().toUInt().toString(16)
        return "$sourceUrl#news-$index-$fallbackId"
    }

    private fun buildCandidateRoots(titleElement: Element, itemRoot: Element?): List<Element> {
        val result = mutableListOf<Element>()
        result.add(titleElement)
        itemRoot?.let { result.add(it) }
        itemRoot?.parent()?.let { result.add(it) }
        itemRoot?.parent()?.parent()?.let { result.add(it) }
        titleElement.parents().take(MAX_PARENT_SCAN).forEach { result.add(it) }
        titleElement.ownerDocument()?.body()?.let { result.add(it) }
        return result.distinct()
    }

    private fun parseWithTimeOnly(value: String): Long? {
        val match = TIME_ONLY_REGEX.find(value) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: return null
        val minutes = match.groupValues[2].toIntOrNull() ?: return null
        if (hours !in 0..23 || minutes !in 0..59) return null
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val localDateTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hours, minutes))
        return localDateTime.atZone(zone).toInstant().toEpochMilli()
    }

    private fun parseWithDayMonthAndTime(value: String): Long? {
        val match = DAY_MONTH_TIME_REGEX.find(value) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val monthRaw = match.groupValues[2].lowercase().trim('.')
        val month = MONTH_MAP[monthRaw] ?: return null
        val hours = match.groupValues[3].toIntOrNull() ?: return null
        val minutes = match.groupValues[4].toIntOrNull() ?: return null
        if (hours !in 0..23 || minutes !in 0..59) return null
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val year = now.year
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        val dateTime = LocalDateTime.of(date, LocalTime.of(hours, minutes)).atZone(zone)
        return dateTime.toInstant().toEpochMilli()
    }

    private companion object {
        private const val DEFAULT_ITEM_TIME_STEP_MS = 60_000L
        private const val TAG_WEBSITE_PARSER = "WebsiteParser"
        private const val MAX_CLASS_HISTOGRAM_ITEMS = 12
        private const val MAX_ANCHOR_SAMPLES = 8
        private const val MAX_PARENT_SCAN = 4
        private const val LOG_CONTENT_PREVIEW_LEN = 140
        private val TIME_ONLY_REGEX = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
        private val DAY_MONTH_TIME_REGEX =
            Regex("""\b(\d{1,2})\s+([а-яА-Яa-zA-ZіїєґІЇЄҐ\.]+)\s*,?\s*([01]?\d|2[0-3]):([0-5]\d)\b""")
        private val MONTH_MAP = mapOf(
            "січ" to Month.JANUARY, "січня" to Month.JANUARY, "jan" to Month.JANUARY,
            "лют" to Month.FEBRUARY, "лютого" to Month.FEBRUARY, "feb" to Month.FEBRUARY,
            "бер" to Month.MARCH, "березня" to Month.MARCH, "mar" to Month.MARCH,
            "кві" to Month.APRIL, "квітня" to Month.APRIL, "apr" to Month.APRIL,
            "тра" to Month.MAY, "травня" to Month.MAY, "may" to Month.MAY,
            "чер" to Month.JUNE, "червня" to Month.JUNE, "jun" to Month.JUNE,
            "лип" to Month.JULY, "липня" to Month.JULY, "jul" to Month.JULY,
            "сер" to Month.AUGUST, "серпня" to Month.AUGUST, "aug" to Month.AUGUST,
            "вер" to Month.SEPTEMBER, "вересня" to Month.SEPTEMBER, "sep" to Month.SEPTEMBER,
            "жов" to Month.OCTOBER, "жовтня" to Month.OCTOBER, "oct" to Month.OCTOBER,
            "лис" to Month.NOVEMBER, "листопада" to Month.NOVEMBER, "nov" to Month.NOVEMBER,
            "гру" to Month.DECEMBER, "грудня" to Month.DECEMBER, "dec" to Month.DECEMBER,
            "янв" to Month.JANUARY, "фев" to Month.FEBRUARY, "марта" to Month.MARCH,
            "апр" to Month.APRIL, "июн" to Month.JUNE, "июл" to Month.JULY,
            "авг" to Month.AUGUST, "сен" to Month.SEPTEMBER, "окт" to Month.OCTOBER,
            "ноя" to Month.NOVEMBER, "дек" to Month.DECEMBER
        )
    }
}

package com.andrewwin.sumup.data.remote

import android.util.Log
import com.andrewwin.sumup.data.local.entities.Article
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
        
        val formatters = formattersThreadLocal.get()
        for (f in formatters) {
            runCatching { f.parse(trimmed)?.time }.getOrNull()?.let { return it }
        }

        return parseWithDayMonthAndTime(trimmed) ?: parseWithTimeOnly(trimmed) ?: fallbackMillis
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
        
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hours)
            set(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun parseWithDayMonthAndTime(value: String): Long? {
        val match = DAY_MONTH_TIME_REGEX.find(value) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val monthRaw = match.groupValues[2].lowercase().trim('.')
        val month = MONTH_MAP[monthRaw] ?: return null
        val hours = match.groupValues[3].toIntOrNull() ?: return null
        val minutes = match.groupValues[4].toIntOrNull() ?: return null
        if (hours !in 0..23 || minutes !in 0..59) return null

        return Calendar.getInstance().apply {
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hours)
            set(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
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

        private val formattersThreadLocal = ThreadLocal.withInitial {
            listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }
        }

        private val MONTH_MAP = mapOf(
            "січ" to Calendar.JANUARY, "січня" to Calendar.JANUARY, "jan" to Calendar.JANUARY,
            "лют" to Calendar.FEBRUARY, "лютого" to Calendar.FEBRUARY, "feb" to Calendar.FEBRUARY,
            "бер" to Calendar.MARCH, "березня" to Calendar.MARCH, "mar" to Calendar.MARCH,
            "кві" to Calendar.APRIL, "квітня" to Calendar.APRIL, "apr" to Calendar.APRIL,
            "тра" to Calendar.MAY, "травня" to Calendar.MAY, "may" to Calendar.MAY,
            "чер" to Calendar.JUNE, "червня" to Calendar.JUNE, "jun" to Calendar.JUNE,
            "лип" to Calendar.JULY, "липня" to Calendar.JULY, "jul" to Calendar.JULY,
            "сер" to Calendar.AUGUST, "серпня" to Calendar.AUGUST, "aug" to Calendar.AUGUST,
            "вер" to Calendar.SEPTEMBER, "вересня" to Calendar.SEPTEMBER, "sep" to Calendar.SEPTEMBER,
            "жов" to Calendar.OCTOBER, "жовтня" to Calendar.OCTOBER, "oct" to Calendar.OCTOBER,
            "лис" to Calendar.NOVEMBER, "листопада" to Calendar.NOVEMBER, "nov" to Calendar.NOVEMBER,
            "гру" to Calendar.DECEMBER, "грудня" to Calendar.DECEMBER, "dec" to Calendar.DECEMBER,
            "янв" to Calendar.JANUARY, "фев" to Calendar.FEBRUARY, "марта" to Calendar.MARCH,
            "апр" to Calendar.APRIL, "июн" to Calendar.JUNE, "июл" to Calendar.JULY,
            "авг" to Calendar.AUGUST, "сен" to Calendar.SEPTEMBER, "окт" to Calendar.OCTOBER,
            "ноя" to Calendar.NOVEMBER, "дек" to Calendar.DECEMBER
        )
    }
}

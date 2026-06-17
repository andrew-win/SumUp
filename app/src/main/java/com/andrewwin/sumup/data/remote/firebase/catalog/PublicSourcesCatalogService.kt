package com.andrewwin.sumup.data.remote.firebase.catalog

import com.andrewwin.sumup.domain.source.model.SourceType
import com.andrewwin.sumup.domain.source.repository.ImportedSource
import com.andrewwin.sumup.domain.source.repository.ImportedSourceGroup
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.get

@Singleton
class PublicSourcesCatalogService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun fetchGroups(): List<ImportedSourceGroup> {
        val document = firestore.collection(COLLECTION_NAME).document(DOCUMENT_ID).get().await()
        val groups = document.get(KEY_GROUPS) as? List<*> ?: emptyList<Any>()
        return groups.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            toImportedGroup(map)
        }
    }

    private fun toImportedGroup(groupMap: Map<*, *>): ImportedSourceGroup? {
        val groupId = (groupMap[KEY_ID] as? String).orEmpty().trim().ifBlank {
            (groupMap[KEY_NAME] as? String).orEmpty().trim().lowercase()
        }
        val titleMap = groupMap[KEY_TITLE] as? Map<*, *>
        val nameUk = (titleMap?.get(KEY_LANGUAGE_UK) as? String).orEmpty().trim()
            .ifBlank { (groupMap[KEY_NAME_UK] as? String).orEmpty().trim() }
            .ifBlank { (groupMap[KEY_NAME] as? String).orEmpty().trim() }
        val nameEn = (titleMap?.get(KEY_LANGUAGE_EN) as? String).orEmpty().trim()
            .ifBlank { (groupMap[KEY_NAME_EN] as? String).orEmpty().trim() }
            .ifBlank { nameUk }
        val name = nameUk.ifBlank { nameEn }
        if (groupId.isBlank() || name.isBlank()) return null

        val sourceMaps = groupMap[KEY_SOURCES] as? List<*> ?: emptyList<Any>()
        val sources = sourceMaps.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val sourceName = (map[KEY_NAME] as? String).orEmpty().trim()
            val sourceUrl = (map[KEY_URL] as? String).orEmpty().trim()
            val sourceType = runCatching {
                SourceType.valueOf((map[KEY_TYPE] as? String).orEmpty().trim().uppercase())
            }.getOrNull() ?: return@mapNotNull null
            if (sourceName.isBlank() || sourceUrl.isBlank()) return@mapNotNull null

            ImportedSource(
                name = sourceName,
                url = sourceUrl,
                type = sourceType,
                isEnabled = map[KEY_IS_ENABLED] as? Boolean ?: true,
                footerPattern = (map[KEY_FOOTER_PATTERN] as? String)?.trim()
                    ?.takeIf { it.isNotEmpty() },
                footerPatternCheckedAt = (map[KEY_FOOTER_PATTERN_CHECKED_AT] as? Number)?.toLong() ?: 0L,
                titleSelector = (map[KEY_TITLE_SELECTOR] as? String)?.trim()
                    ?.takeIf { it.isNotEmpty() },
                postLinkSelector = (map[KEY_POST_LINK_SELECTOR] as? String)?.trim()
                    ?.takeIf { it.isNotEmpty() },
                descriptionSelector = (map[KEY_DESCRIPTION_SELECTOR] as? String)?.trim()
                    ?.takeIf { it.isNotEmpty() },
                dateSelector = (map[KEY_DATE_SELECTOR] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                useHeadlessBrowser = map[KEY_USE_HEADLESS_BROWSER] as? Boolean ?: false
            )
        }

        val recommendationAnchors = (groupMap[KEY_ANCHORS] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
            ?: (groupMap[KEY_EXAMPLES] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
                .orEmpty()

        return ImportedSourceGroup(
            id = groupId,
            name = name,
            nameUk = nameUk,
            nameEn = nameEn,
            isEnabled = groupMap[KEY_IS_ENABLED] as? Boolean ?: true,
            isDeletable = groupMap[KEY_IS_DELETABLE] as? Boolean ?: true,
            sources = sources,
            recommendationAnchors = recommendationAnchors,
            sortOrder = (groupMap[KEY_SORT_ORDER] as? Number)?.toInt() ?: 0
        )
    }

    companion object {
        const val COLLECTION_NAME = "public_source_catalog"
        const val DOCUMENT_ID = "default"
        private const val KEY_GROUPS = "groups"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_NAME_UK = "nameUk"
        private const val KEY_NAME_EN = "nameEn"
        private const val KEY_TITLE = "title"
        private const val KEY_LANGUAGE_UK = "uk"
        private const val KEY_LANGUAGE_EN = "en"
        private const val KEY_SOURCES = "sources"
        private const val KEY_URL = "url"
        private const val KEY_TYPE = "type"
        private const val KEY_IS_ENABLED = "isEnabled"
        private const val KEY_IS_DELETABLE = "isDeletable"
        private const val KEY_FOOTER_PATTERN = "footerPattern"
        private const val KEY_FOOTER_PATTERN_CHECKED_AT = "footerPatternCheckedAt"
        private const val KEY_TITLE_SELECTOR = "titleSelector"
        private const val KEY_POST_LINK_SELECTOR = "postLinkSelector"
        private const val KEY_DESCRIPTION_SELECTOR = "descriptionSelector"
        private const val KEY_DATE_SELECTOR = "dateSelector"
        private const val KEY_USE_HEADLESS_BROWSER = "useHeadlessBrowser"
        private const val KEY_ANCHORS = "anchors"
        private const val KEY_EXAMPLES = "examples"
        private const val KEY_SORT_ORDER = "sortOrder"
    }
}

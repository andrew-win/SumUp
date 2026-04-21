package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.repository.ImportedSource
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicSourcesCatalogService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun fetchGroups(): List<ImportedSourceGroup> {
        val document = firestore.collection(COLLECTION_NAME).document(DOCUMENT_ID).get().await()
        val groups = document.get("groups") as? List<*> ?: emptyList<Any>()
        return groups.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            toImportedGroup(map)
        }
    }

    private fun toImportedGroup(groupMap: Map<*, *>): ImportedSourceGroup? {
        val groupId = (groupMap["id"] as? String).orEmpty().trim().ifBlank {
            (groupMap["name"] as? String).orEmpty().trim().lowercase()
        }
        val titleMap = groupMap["title"] as? Map<*, *>
        val nameUk = (titleMap?.get("uk") as? String).orEmpty().trim()
            .ifBlank { (groupMap["nameUk"] as? String).orEmpty().trim() }
            .ifBlank { (groupMap["name"] as? String).orEmpty().trim() }
        val nameEn = (titleMap?.get("en") as? String).orEmpty().trim()
            .ifBlank { (groupMap["nameEn"] as? String).orEmpty().trim() }
            .ifBlank { nameUk }
        val name = nameUk.ifBlank { nameEn }
        if (groupId.isBlank() || name.isBlank()) return null

        val sourceMaps = groupMap["sources"] as? List<*> ?: emptyList<Any>()
        val sources = sourceMaps.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val sourceName = (map["name"] as? String).orEmpty().trim()
            val sourceUrl = (map["url"] as? String).orEmpty().trim()
            val sourceType = runCatching {
                SourceType.valueOf((map["type"] as? String).orEmpty().trim().uppercase())
            }.getOrNull() ?: return@mapNotNull null
            if (sourceName.isBlank() || sourceUrl.isBlank()) return@mapNotNull null

            ImportedSource(
                name = sourceName,
                url = sourceUrl,
                type = sourceType,
                isEnabled = map["isEnabled"] as? Boolean ?: true,
                footerPattern = (map["footerPattern"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                titleSelector = (map["titleSelector"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                postLinkSelector = (map["postLinkSelector"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                descriptionSelector = (map["descriptionSelector"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                dateSelector = (map["dateSelector"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                useHeadlessBrowser = map["useHeadlessBrowser"] as? Boolean ?: false
            )
        }

        return ImportedSourceGroup(
            id = groupId,
            name = name,
            nameUk = nameUk,
            nameEn = nameEn,
            isEnabled = groupMap["isEnabled"] as? Boolean ?: true,
            isDeletable = groupMap["isDeletable"] as? Boolean ?: true,
            sources = sources
        )
    }

    companion object {
        const val COLLECTION_NAME = "public_source_catalog"
        const val DOCUMENT_ID = "default"
    }
}

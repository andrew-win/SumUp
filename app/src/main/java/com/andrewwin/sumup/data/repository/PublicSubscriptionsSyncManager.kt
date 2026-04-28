package com.andrewwin.sumup.data.repository

import android.content.Context
import com.andrewwin.sumup.data.remote.PublicSourcesCatalogService
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicSubscriptionsSyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val publicSourcesCatalogService: PublicSourcesCatalogService
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val syncStateMutex = Mutex()
    private var activeSync: CompletableDeferred<Boolean>? = null

    suspend fun syncIfDue(): Boolean {
        val lastAttemptAt = prefs.getLong(KEY_LAST_ATTEMPT_AT, 0L)
        val isDue = System.currentTimeMillis() - lastAttemptAt >= SYNC_COOLDOWN_MS
        return if (isDue) sync(force = false) else true
    }

    suspend fun sync(force: Boolean): Boolean {
        val (syncResult, shouldStartSync) = registerOrJoinActiveSync()
        if (!shouldStartSync) return syncResult.await()

        val success = try {
            val lastAttemptAt = prefs.getLong(KEY_LAST_ATTEMPT_AT, 0L)
            if (!force && System.currentTimeMillis() - lastAttemptAt < SYNC_COOLDOWN_MS) {
                true
            } else {
                prefs.edit()
                    .putLong(KEY_LAST_ATTEMPT_AT, System.currentTimeMillis())
                    .apply()

                runCatching {
                    val remoteGroups = publicSourcesCatalogService.fetchGroups()
                    saveSuccessState(remoteGroups)
                }.isSuccess.also { syncSucceeded ->
                    if (!syncSucceeded) {
                        prefs.edit()
                            .putBoolean(KEY_LAST_SYNC_FAILED, true)
                            .apply()
                    }
                }
            }
        } catch (_: Exception) {
            false
        }

        syncResult.complete(success)
        clearActiveSync(syncResult)
        return success
    }

    fun hasSyncFailure(): Boolean = prefs.getBoolean(KEY_LAST_SYNC_FAILED, false)

    fun getCachedGroups(): List<ImportedSourceGroup> {
        val json = prefs.getString(KEY_CACHED_GROUPS_JSON, null).orEmpty()
        if (json.isBlank()) return emptyList()
        val groups = JSONArray(json)
        return buildList {
            for (index in 0 until groups.length()) {
                val item = groups.optJSONObject(index)?.toImportedGroup() ?: continue
                add(item)
            }
        }
    }

    private fun saveSuccessState(groups: List<ImportedSourceGroup>) {
        prefs.edit()
            .putLong(KEY_LAST_SUCCESS_AT, System.currentTimeMillis())
            .putBoolean(KEY_LAST_SYNC_FAILED, false)
            .putString(KEY_CACHED_GROUPS_JSON, groups.toJson().toString())
            .apply()
    }

    private fun List<ImportedSourceGroup>.toJson(): JSONArray = JSONArray().apply {
        forEach { group ->
            put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("nameUk", group.nameUk)
                put("nameEn", group.nameEn)
                put("title", JSONObject().apply {
                    put("uk", group.nameUk)
                    put("en", group.nameEn)
                })
                put("isEnabled", group.isEnabled)
                put("isDeletable", group.isDeletable)
                put("sources", JSONArray().apply {
                    group.sources.forEach { source ->
                        put(JSONObject().apply {
                            put("name", source.name)
                            put("url", source.url)
                            put("type", source.type.name)
                            put("isEnabled", source.isEnabled)
                            source.footerPattern?.let { put("footerPattern", it) }
                            source.titleSelector?.let { put("titleSelector", it) }
                            source.postLinkSelector?.let { put("postLinkSelector", it) }
                            source.descriptionSelector?.let { put("descriptionSelector", it) }
                            source.dateSelector?.let { put("dateSelector", it) }
                            put("useHeadlessBrowser", source.useHeadlessBrowser)
                        })
                    }
                })
                put("anchors", JSONArray().apply {
                    group.recommendationAnchors.forEach { put(it) }
                })
                put("sortOrder", group.sortOrder)
            })
        }
    }

    private fun JSONObject.toImportedGroup(): ImportedSourceGroup? {
        val titleJson = optJSONObject("title")
        val nameUk = titleJson?.optString("uk").orEmpty().trim()
            .ifBlank { optString("nameUk").trim() }
            .ifBlank { optString("name").trim() }
        val nameEn = titleJson?.optString("en").orEmpty().trim()
            .ifBlank { optString("nameEn").trim() }
            .ifBlank { nameUk }
        val name = nameUk.ifBlank { nameEn }
        val groupId = optString("id").trim().ifBlank { name.lowercase() }
        if (groupId.isBlank() || name.isBlank()) return null
        val sourcesJson = optJSONArray("sources") ?: JSONArray()
        val sources = buildList {
            for (index in 0 until sourcesJson.length()) {
                val sourceJson = sourcesJson.optJSONObject(index) ?: continue
                val sourceName = sourceJson.optString("name").trim()
                val sourceUrl = sourceJson.optString("url").trim()
                val sourceType = runCatching {
                    com.andrewwin.sumup.data.local.entities.SourceType.valueOf(
                        sourceJson.optString("type").trim().uppercase()
                    )
                }.getOrNull() ?: continue
                if (sourceName.isBlank() || sourceUrl.isBlank()) continue
                add(
                    com.andrewwin.sumup.domain.repository.ImportedSource(
                        name = sourceName,
                        url = sourceUrl,
                        type = sourceType,
                        isEnabled = sourceJson.optBoolean("isEnabled", true),
                        footerPattern = sourceJson.optString("footerPattern").trim().takeIf { it.isNotEmpty() },
                        titleSelector = sourceJson.optString("titleSelector").trim().takeIf { it.isNotEmpty() },
                        postLinkSelector = sourceJson.optString("postLinkSelector").trim().takeIf { it.isNotEmpty() },
                        descriptionSelector = sourceJson.optString("descriptionSelector").trim().takeIf { it.isNotEmpty() },
                        dateSelector = sourceJson.optString("dateSelector").trim().takeIf { it.isNotEmpty() },
                        useHeadlessBrowser = sourceJson.optBoolean("useHeadlessBrowser", false)
                    )
                )
            }
        }
        val recommendationAnchors = optJSONArray("anchors")
            ?.let { anchorsJson ->
                buildList {
                    for (index in 0 until anchorsJson.length()) {
                        val anchor = anchorsJson.optString(index).trim()
                        if (anchor.isNotEmpty()) add(anchor)
                    }
                }
            }
            ?: emptyList()
        return ImportedSourceGroup(
            id = groupId,
            name = name,
            nameUk = nameUk,
            nameEn = nameEn,
            isEnabled = optBoolean("isEnabled", true),
            isDeletable = optBoolean("isDeletable", true),
            sources = sources,
            recommendationAnchors = recommendationAnchors,
            sortOrder = optInt("sortOrder", 0)
        )
    }

    private suspend fun registerOrJoinActiveSync(): Pair<CompletableDeferred<Boolean>, Boolean> =
        syncStateMutex.withLock {
            activeSync?.let { return it to false }
            CompletableDeferred<Boolean>().also { activeSync = it } to true
        }

    private suspend fun clearActiveSync(syncResult: CompletableDeferred<Boolean>) {
        syncStateMutex.withLock {
            if (activeSync === syncResult) {
                activeSync = null
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "public_subscriptions_sync"
        private const val KEY_LAST_SUCCESS_AT = "lastSuccessAt"
        private const val KEY_LAST_ATTEMPT_AT = "lastAttemptAt"
        private const val KEY_LAST_SYNC_FAILED = "lastSyncFailed"
        private const val KEY_CACHED_GROUPS_JSON = "cachedGroupsJson"
        const val SYNC_COOLDOWN_MS = 30L * 60L * 1000L
    }
}

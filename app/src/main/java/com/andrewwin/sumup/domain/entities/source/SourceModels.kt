package com.andrewwin.sumup.domain.entities.source

data class Source(
    val id: Long = 0,
    val groupId: Long,
    val name: String,
    val url: String,
    val type: SourceType,
    val isEnabled: Boolean = true,
    val footerPattern: String? = null,
    val footerPatternCheckedAt: Long = 0L,
    val titleSelector: String? = null,
    val postLinkSelector: String? = null,
    val descriptionSelector: String? = null,
    val dateSelector: String? = null,
    val useHeadlessBrowser: Boolean = false
)

data class SourceGroup(
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val isDeletable: Boolean = true,
    val origin: SourceGroupOrigin = SourceGroupOrigin.USER,
    val subscriptionId: String? = null
)

data class SourceGroupWithSources(
    val group: SourceGroup,
    val sources: List<Source>
)

enum class SourceType {
    TELEGRAM,
    RSS,
    YOUTUBE
}

enum class SourceGroupOrigin {
    USER,
    PUBLIC_SUBSCRIPTION,
    SYSTEM
}

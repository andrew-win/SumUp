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

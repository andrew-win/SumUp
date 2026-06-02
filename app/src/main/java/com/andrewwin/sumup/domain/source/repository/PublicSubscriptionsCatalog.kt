package com.andrewwin.sumup.domain.source.repository

interface PublicSubscriptionsCatalog {
    fun getCachedGroups(): List<ImportedSourceGroup>
}

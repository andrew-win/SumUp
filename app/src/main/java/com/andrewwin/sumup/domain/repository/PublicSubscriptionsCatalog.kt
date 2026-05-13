package com.andrewwin.sumup.domain.repository

interface PublicSubscriptionsCatalog {
    fun getCachedGroups(): List<ImportedSourceGroup>
}

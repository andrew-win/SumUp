package com.andrewwin.sumup.ui.screen.sources

import com.andrewwin.sumup.domain.entities.source.SourceGroup
import com.andrewwin.sumup.domain.entities.source.SourceGroupOrigin

internal fun canAddSourceToGroup(group: SourceGroup): Boolean =
    group.isEnabled && !isPublicSubscriptionGroup(group)

internal fun canEditGroup(group: SourceGroup): Boolean =
    group.isDeletable && !isPublicSubscriptionGroup(group)

internal fun canDeleteGroup(group: SourceGroup): Boolean =
    group.isDeletable

internal fun canEditSourceInGroup(group: SourceGroup): Boolean =
    !isPublicSubscriptionGroup(group)

internal fun canDeleteSourceFromGroup(group: SourceGroup): Boolean =
    !isPublicSubscriptionGroup(group)

private fun isPublicSubscriptionGroup(group: SourceGroup): Boolean =
    group.origin == SourceGroupOrigin.PUBLIC_SUBSCRIPTION

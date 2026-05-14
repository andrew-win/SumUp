package com.andrewwin.sumup.ui.screen.sources

import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceGroupOrigin

internal fun canAddSourceToGroup(group: SourceGroup): Boolean =
    group.isEnabled && group.origin != SourceGroupOrigin.PUBLIC_SUBSCRIPTION

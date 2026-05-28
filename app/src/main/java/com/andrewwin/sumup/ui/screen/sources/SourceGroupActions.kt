package com.andrewwin.sumup.ui.screen.sources

import com.andrewwin.sumup.domain.source.SourceGroup
import com.andrewwin.sumup.domain.source.SourceGroupOrigin

internal fun canAddSourceToGroup(group: SourceGroup): Boolean =
    group.isEnabled && group.origin != SourceGroupOrigin.PUBLIC_SUBSCRIPTION

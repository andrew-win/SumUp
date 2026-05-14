package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceGroupOrigin
import com.andrewwin.sumup.ui.screen.sources.canAddSourceToGroup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceGroupActionsTest {
    @Test
    fun `can add source to enabled user group`() {
        val group = SourceGroup(
            name = "Manual group",
            isEnabled = true,
            origin = SourceGroupOrigin.USER
        )

        assertTrue(canAddSourceToGroup(group))
    }

    @Test
    fun `cannot add source to disabled group`() {
        val group = SourceGroup(
            name = "Manual group",
            isEnabled = false,
            origin = SourceGroupOrigin.USER
        )

        assertFalse(canAddSourceToGroup(group))
    }

    @Test
    fun `cannot add source to public subscription group`() {
        val group = SourceGroup(
            name = "Subscription",
            isEnabled = true,
            isDeletable = true,
            origin = SourceGroupOrigin.PUBLIC_SUBSCRIPTION,
            subscriptionId = "news"
        )

        assertFalse(canAddSourceToGroup(group))
    }
}

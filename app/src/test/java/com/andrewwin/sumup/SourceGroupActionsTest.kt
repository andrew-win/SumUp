package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceGroupOrigin
import com.andrewwin.sumup.ui.screen.sources.canAddSourceToGroup
import com.andrewwin.sumup.ui.screen.sources.canDeleteGroup
import com.andrewwin.sumup.ui.screen.sources.canDeleteSourceFromGroup
import com.andrewwin.sumup.ui.screen.sources.canEditGroup
import com.andrewwin.sumup.ui.screen.sources.canEditSourceInGroup
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

    @Test
    fun `cannot edit public subscription group`() {
        val group = SourceGroup(
            name = "Subscription",
            isEnabled = true,
            isDeletable = true,
            origin = SourceGroupOrigin.PUBLIC_SUBSCRIPTION,
            subscriptionId = "news"
        )

        assertFalse(canEditGroup(group))
    }

    @Test
    fun `cannot delete source from public subscription group`() {
        val group = SourceGroup(
            name = "Subscription",
            isEnabled = true,
            isDeletable = true,
            origin = SourceGroupOrigin.PUBLIC_SUBSCRIPTION,
            subscriptionId = "news"
        )

        assertFalse(canDeleteSourceFromGroup(group))
    }

    @Test
    fun `can delete public subscription group`() {
        val group = SourceGroup(
            name = "Subscription",
            isEnabled = true,
            isDeletable = true,
            origin = SourceGroupOrigin.PUBLIC_SUBSCRIPTION,
            subscriptionId = "news"
        )

        assertTrue(canDeleteGroup(group))
    }

    @Test
    fun `cannot edit source in public subscription group`() {
        val group = SourceGroup(
            name = "Subscription",
            isEnabled = true,
            isDeletable = true,
            origin = SourceGroupOrigin.PUBLIC_SUBSCRIPTION,
            subscriptionId = "news"
        )

        assertFalse(canEditSourceInGroup(group))
    }

    @Test
    fun `can edit user group when deletable`() {
        val group = SourceGroup(
            name = "Manual group",
            isEnabled = true,
            isDeletable = true,
            origin = SourceGroupOrigin.USER
        )

        assertTrue(canEditGroup(group))
    }

    @Test
    fun `can edit source in user group`() {
        val group = SourceGroup(
            name = "Manual group",
            isEnabled = true,
            isDeletable = true,
            origin = SourceGroupOrigin.USER
        )

        assertTrue(canEditSourceInGroup(group))
    }

    @Test
    fun `can delete source from user group`() {
        val group = SourceGroup(
            name = "Manual group",
            isEnabled = true,
            isDeletable = true,
            origin = SourceGroupOrigin.USER
        )

        assertTrue(canDeleteSourceFromGroup(group))
    }
}

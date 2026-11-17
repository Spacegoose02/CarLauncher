package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.maps.LaunchStrategy
import com.spacegoose.carlauncher.maps.MapLaunchMode
import com.spacegoose.carlauncher.maps.MapLaunchPlanner
import com.spacegoose.carlauncher.maps.WindowCapabilities
import org.junit.Assert.assertEquals
import org.junit.Test

class MapLaunchPlannerTest {
    private fun caps(
        sdk: Int = 30,
        split: Boolean = true,
        inMultiWindow: Boolean = false,
        freeform: Boolean = false,
        helper: Boolean = false,
    ) = WindowCapabilities(sdk, split, inMultiWindow, freeform, helper)

    @Test fun `android 12L and later launch adjacent from full screen`() {
        assertEquals(LaunchStrategy.LAUNCH_ADJACENT, MapLaunchPlanner.plan(MapLaunchMode.AUTO, caps(sdk = 33)))
    }

    @Test fun `already in split-screen launches adjacent on any version`() {
        assertEquals(LaunchStrategy.LAUNCH_ADJACENT, MapLaunchPlanner.plan(MapLaunchMode.AUTO, caps(sdk = 29, inMultiWindow = true)))
    }

    @Test fun `android 10 head unit uses the accessibility helper when it is on`() {
        assertEquals(
            LaunchStrategy.ENTER_SPLIT_THEN_ADJACENT,
            MapLaunchPlanner.plan(MapLaunchMode.AUTO, caps(sdk = 29, helper = true, freeform = true)),
        )
    }

    @Test fun `android 10 without the helper falls back to freeform, then full screen`() {
        assertEquals(LaunchStrategy.FREEFORM, MapLaunchPlanner.plan(MapLaunchMode.AUTO, caps(sdk = 29, freeform = true)))
        assertEquals(LaunchStrategy.FULLSCREEN, MapLaunchPlanner.plan(MapLaunchMode.AUTO, caps(sdk = 29)))
    }

    @Test fun `no split-screen support skips split`() {
        assertEquals(LaunchStrategy.FREEFORM, MapLaunchPlanner.plan(MapLaunchMode.AUTO, caps(sdk = 34, split = false, freeform = true)))
        assertEquals(LaunchStrategy.FULLSCREEN, MapLaunchPlanner.plan(MapLaunchMode.SPLIT_SCREEN, caps(sdk = 34, split = false)))
    }

    @Test fun `forced modes`() {
        assertEquals(LaunchStrategy.FULLSCREEN, MapLaunchPlanner.plan(MapLaunchMode.FULLSCREEN, caps(sdk = 34)))
        assertEquals(LaunchStrategy.FREEFORM, MapLaunchPlanner.plan(MapLaunchMode.FREEFORM, caps(sdk = 34, freeform = true)))
        assertEquals(LaunchStrategy.FULLSCREEN, MapLaunchPlanner.plan(MapLaunchMode.FREEFORM, caps(sdk = 34)))
    }
}

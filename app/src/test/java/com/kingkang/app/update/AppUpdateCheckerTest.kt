package com.kingkang.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun `newer patch and minor versions are updates`() {
        assertTrue(AppUpdateChecker.isNewer("0.2.6", "0.2.5"))
        assertTrue(AppUpdateChecker.isNewer("0.3.0", "0.2.9"))
        assertTrue(AppUpdateChecker.isNewer("v0.2.5", "0.2.4"))
    }

    @Test
    fun `equal or older versions are not updates`() {
        assertFalse(AppUpdateChecker.isNewer("0.2.5", "0.2.5"))
        assertFalse(AppUpdateChecker.isNewer("0.2.4", "0.2.5"))
    }

    @Test
    fun `stable release outranks a local prerelease`() {
        assertTrue(AppUpdateChecker.isNewer("0.2.5", "0.2.5-rc.2"))
        assertFalse(AppUpdateChecker.isNewer("0.2.5-rc.2", "0.2.5"))
    }
}

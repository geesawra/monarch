package com.example.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: throw Exception("targetAppId not passed as instrumentation runner arg"),
            includeInStartupProfile = true
        ) {
            device.executeShellCommand("svc power stayon true")
            pressHome()
            startActivityAndWait {
                it.putExtra("baseline_profile_mode", true)
            }

            loginIfNeeded()

            device.wait(Until.hasObject(By.scrollable(true)), 15_000)

            scrollTimeline()
            openAndScrollThread()
            openAndScrollProfile()
            switchToNotifications()
            switchToSearch()

            device.executeShellCommand("svc power stayon false")
        }
    }

    private fun MacroScope.loginIfNeeded() {
        val args = InstrumentationRegistry.getArguments()
        val handle = args.getString("benchmarkHandle") ?: return
        val password = args.getString("benchmarkPassword") ?: return

        device.wait(Until.findObject(By.text("Login")), 3_000) ?: return

        val handleField = device.findObject(By.text("Handle (e.g., yourname.bsky.social)"))
            ?: return
        handleField.click()
        device.waitForIdle()
        device.executeShellCommand("input text $handle")
        device.waitForIdle()

        Thread.sleep(3_000)

        val passwordField = device.findObject(By.text("Password")) ?: return
        passwordField.click()
        device.waitForIdle()
        device.executeShellCommand("input text $password")
        device.waitForIdle()

        device.executeShellCommand("input keyevent KEYCODE_ESCAPE")
        device.waitForIdle()
        Thread.sleep(500)

        device.wait(Until.findObject(By.text("Login")), 2_000)?.click()
        device.waitForIdle()

        val noThanks = device.wait(Until.findObject(By.text("No, thanks")), 3_000)
        noThanks?.click()
        device.waitForIdle()

        device.wait(Until.hasObject(By.scrollable(true)), 30_000)
    }

    private fun MacroScope.findFeedList(): UiObject2? {
        return device.findObject(By.res("feed_list"))
            ?: device.findObjects(By.scrollable(true)).lastOrNull()
    }

    private fun MacroScope.scrollTimeline() {
        repeat(5) {
            try {
                findFeedList()?.scroll(Direction.DOWN, 80f)
            } catch (_: StaleObjectException) {}
            device.waitForIdle()
        }

        repeat(3) {
            try {
                findFeedList()?.fling(Direction.DOWN)
            } catch (_: StaleObjectException) {}
            Thread.sleep(500)
        }

        device.waitForIdle()

        repeat(3) {
            try {
                findFeedList()?.fling(Direction.UP)
            } catch (_: StaleObjectException) {}
            Thread.sleep(500)
        }

        device.waitForIdle()
    }

    private fun MacroScope.openAndScrollThread() {
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)

        try {
            findFeedList()?.scroll(Direction.DOWN, 40f)
        } catch (_: StaleObjectException) {}
        device.waitForIdle()

        val list = findFeedList() ?: return
        val post = list.findObject(By.desc("Reply"))?.parent?.parent ?: return
        post.click()
        device.waitForIdle()

        if (device.wait(Until.hasObject(By.text("Thread")), 5_000) == null) {
            device.pressBack()
            device.waitForIdle()
            return
        }

        scrollList("feed_list")

        device.findObject(By.desc("Go back"))?.click()
        device.waitForIdle()
        Thread.sleep(500)
    }

    private fun MacroScope.findList(tag: String? = null): UiObject2? {
        if (tag != null) {
            return device.findObject(By.res(tag))
        }
        return findFeedList()
    }

    private fun MacroScope.scrollList(tag: String? = null, scrollDown: Int = 3, scrollUp: Int = 2) {
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        repeat(scrollDown) {
            try {
                findList(tag)?.scroll(Direction.DOWN, 60f)
            } catch (_: StaleObjectException) {}
            device.waitForIdle()
        }
        repeat(scrollUp) {
            try {
                findList(tag)?.scroll(Direction.UP, 60f)
            } catch (_: StaleObjectException) {}
            device.waitForIdle()
        }
    }

    private fun MacroScope.openAndScrollProfile() {
        device.wait(Until.hasObject(By.desc("Profile avatar")), 5_000)
        val avatar = device.findObject(By.desc("Profile avatar")) ?: return
        avatar.click()
        device.waitForIdle()

        scrollList("profile_list")

        val repliesTab = device.findObject(By.text("Replies"))
        if (repliesTab != null) {
            repliesTab.click()
            device.waitForIdle()
            Thread.sleep(1_000)
            scrollList("profile_list", scrollDown = 2, scrollUp = 0)
        }

        val mediaTab = device.findObject(By.text("Media"))
        if (mediaTab != null) {
            mediaTab.click()
            device.waitForIdle()
            Thread.sleep(1_000)
        }

        device.findObject(By.desc("Go back"))?.click()
        device.waitForIdle()
        Thread.sleep(500)
    }

    private fun MacroScope.switchToNotifications() {
        val notifTab = device.findObject(By.desc("Notifications"))
        if (notifTab != null) {
            notifTab.click()
            device.waitForIdle()

            scrollList("notifications_list")
        }
    }

    private fun MacroScope.switchToSearch() {
        val searchTab = device.findObject(By.desc("Search"))
        if (searchTab != null) {
            searchTab.click()
            device.waitForIdle()
            Thread.sleep(500)
        }

        val timelineTab = device.findObject(By.desc("Timeline"))
        if (timelineTab != null) {
            timelineTab.click()
            device.waitForIdle()
        }
    }
}

private typealias MacroScope = androidx.benchmark.macro.MacrobenchmarkScope

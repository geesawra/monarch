package com.example.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
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
            pressHome()
            startActivityAndWait()

            device.wait(Until.hasObject(By.scrollable(true)), 15_000)

            scrollTimeline()
            openAndScrollThread()
            openAndScrollProfile()
            switchToNotifications()
            switchToSearch()
        }
    }

    private fun MacroScope.scrollTimeline() {
        val list = device.findObject(By.scrollable(true)) ?: return

        repeat(5) {
            list.scroll(Direction.DOWN, 80f)
            device.waitForIdle()
        }

        repeat(3) {
            list.fling(Direction.DOWN)
            Thread.sleep(500)
        }

        device.waitForIdle()

        repeat(3) {
            list.fling(Direction.UP)
            Thread.sleep(500)
        }

        device.waitForIdle()
    }

    private fun MacroScope.openAndScrollThread() {
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        val list = device.findObject(By.scrollable(true)) ?: return

        list.scroll(Direction.DOWN, 40f)
        device.waitForIdle()

        val post = device.findObject(By.desc("Avatar")) ?: return
        post.click()
        device.waitForIdle()

        device.wait(Until.hasObject(By.text("Thread")), 5_000)

        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        val threadList = device.findObject(By.scrollable(true))
        if (threadList != null) {
            repeat(3) {
                threadList.scroll(Direction.DOWN, 60f)
                device.waitForIdle()
            }

            repeat(2) {
                threadList.scroll(Direction.UP, 60f)
                device.waitForIdle()
            }
        }

        val backButton = device.findObject(By.desc("Go back"))
        backButton?.click()
        device.waitForIdle()
        Thread.sleep(500)
    }

    private fun MacroScope.openAndScrollProfile() {
        device.wait(Until.hasObject(By.desc("Profile avatar")), 5_000)
        val avatar = device.findObject(By.desc("Profile avatar"))
        if (avatar != null) {
            avatar.click()
            device.waitForIdle()

            device.wait(Until.hasObject(By.scrollable(true)), 5_000)
            val profileList = device.findObject(By.scrollable(true))
            if (profileList != null) {
                repeat(3) {
                    profileList.scroll(Direction.DOWN, 60f)
                    device.waitForIdle()
                }

                repeat(2) {
                    profileList.scroll(Direction.UP, 60f)
                    device.waitForIdle()
                }
            }

            val repliesTab = device.findObject(By.text("Replies"))
            if (repliesTab != null) {
                repliesTab.click()
                device.waitForIdle()
                Thread.sleep(1_000)

                val repliesList = device.findObject(By.scrollable(true))
                if (repliesList != null) {
                    repeat(2) {
                        repliesList.scroll(Direction.DOWN, 60f)
                        device.waitForIdle()
                    }
                }
            }

            val mediaTab = device.findObject(By.text("Media"))
            if (mediaTab != null) {
                mediaTab.click()
                device.waitForIdle()
                Thread.sleep(1_000)
            }

            val backButton = device.findObject(By.desc("Go back"))
            backButton?.click()
            device.waitForIdle()
            Thread.sleep(500)
        }
    }

    private fun MacroScope.switchToNotifications() {
        val notifTab = device.findObject(By.desc("Notifications"))
        if (notifTab != null) {
            notifTab.click()
            device.waitForIdle()

            device.wait(Until.hasObject(By.scrollable(true)), 5_000)
            val notifList = device.findObject(By.scrollable(true))
            if (notifList != null) {
                repeat(3) {
                    notifList.scroll(Direction.DOWN, 60f)
                    device.waitForIdle()
                }

                repeat(2) {
                    notifList.fling(Direction.UP)
                    Thread.sleep(300)
                }
            }
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

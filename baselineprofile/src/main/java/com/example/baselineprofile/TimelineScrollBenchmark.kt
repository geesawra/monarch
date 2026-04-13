package com.example.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
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

/**
 * Measures scroll jank on the timeline.
 *
 * Prerequisites: the target app must be installed and a user must be logged in on the
 * device (OAuth login cannot be automated from UIAutomator because the Custom Tab runs
 * out-of-process). The baseline profile variant built by the AGP baselineprofile plugin
 * handles installation; you handle the login once, manually.
 *
 * Run from the command line:
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkAndroidTest \
 *   -P android.testInstrumentationRunnerArguments.class=com.example.baselineprofile.TimelineScrollBenchmark
 * ```
 *
 * Or from Android Studio: green gutter arrow next to a @Test method.
 *
 * Output lands in
 * `baselineprofile/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/<device>/`
 * as `*-benchmarkData.json` (percentiles) and one `*.perfetto-trace` per iteration.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
class TimelineScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollCompilationNone() =
        benchmark(CompilationMode.None())

    @Test
    fun scrollCompilationBaselineProfiles() =
        benchmark(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun benchmark(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: throw Exception("targetAppId not passed as instrumentation runner arg"),
            metrics = listOf(
                FrameTimingMetric(),
                TraceSectionMetric(
                    sectionName = "Compose:recompose",
                    mode = TraceSectionMetric.Mode.Sum,
                ),
                TraceSectionMetric(
                    sectionName = "Choreographer#doFrame",
                    mode = TraceSectionMetric.Mode.Sum,
                ),
                TraceSectionMetric(
                    sectionName = "RV CreateView",
                    mode = TraceSectionMetric.Mode.Sum,
                ),
            ),
            compilationMode = compilationMode,
            startupMode = StartupMode.WARM,
            iterations = 1,
            setupBlock = {
                device.executeShellCommand("svc power stayon true")
                pressHome()
                startActivityAndWait()
                device.wait(Until.hasObject(By.res("feed_list")), 15_000)
            },
            measureBlock = {
                val feed = findFeedList() ?: return@measureRepeated

                repeat(3) {
                    try {
                        findFeedList()?.fling(Direction.DOWN)
                    } catch (_: StaleObjectException) {
                    }
                    device.waitForIdle()
                }

                repeat(3) {
                    try {
                        findFeedList()?.fling(Direction.UP)
                    } catch (_: StaleObjectException) {
                    }
                    device.waitForIdle()
                }

                device.executeShellCommand("svc power stayon false")
            },
        )
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.findFeedList(): UiObject2? =
        device.findObject(By.res("feed_list"))
            ?: device.findObjects(By.scrollable(true)).lastOrNull()
}

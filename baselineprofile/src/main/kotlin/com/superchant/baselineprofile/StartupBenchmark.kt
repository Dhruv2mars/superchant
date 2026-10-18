package com.superchant.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Cold start and touch counting, without any AOT compilation and with the baseline profile:
 * `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(Parameterized::class)
class StartupBenchmark(private val compilation: CompilationMode) {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStart() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { grantNotifications(); pressHome() }
    ) {
        startActivityAndWait()
    }

    @OptIn(androidx.benchmark.macro.ExperimentalMetricApi::class)
    @Test
    fun touchCounting() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric("SC.increment", TraceSectionMetric.Mode.Average),
            TraceSectionMetric("SC.ringDraw", TraceSectionMetric.Mode.Average)
        ),
        compilationMode = compilation,
        iterations = 5,
        setupBlock = {
            grantNotifications()
            pressHome()
            startSession()
        }
    ) {
        tapCounter(40)
        stopSession()
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun modes() = listOf(CompilationMode.None(), CompilationMode.Partial(BaselineProfileMode.Require))
    }
}

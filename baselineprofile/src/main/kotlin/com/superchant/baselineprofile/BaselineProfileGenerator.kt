package com.superchant.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's profiles: `./gradlew :app:generateReleaseBaselineProfile` with the emulator running.
 * The startup profile (dex layout) covers launch only; the baseline profile adds counting and every screen.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        grantNotifications()
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun chantingAndScreens() = rule.collect(packageName = PACKAGE) {
        grantNotifications()
        pressHome()
        startActivityAndWait()
        startSession()
        tapCounter(30)
        stopSession()
        visitScreens()
    }
}

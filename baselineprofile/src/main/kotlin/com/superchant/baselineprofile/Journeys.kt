package com.superchant.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val PACKAGE = "com.superchant"
private const val TIMEOUT_MS = 5_000L

/** Lets Start run without the notification prompt (it only asks; counting never waits on it). */
internal fun MacrobenchmarkScope.grantNotifications() {
    device.executeShellCommand("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
}

private fun MacrobenchmarkScope.find(label: String): UiObject2? {
    for (selector in listOf<BySelector>(By.desc(label), By.text(label))) {
        device.wait(Until.findObject(selector), TIMEOUT_MS)?.let { return it }
    }
    return null
}

/** Starts a free session the way the launcher shortcut does (through Home), with touch counting on. */
internal fun MacrobenchmarkScope.startSession() {
    startActivityAndWait(
        Intent("com.superchant.action.START_COUNTING").setClassName(PACKAGE, "$PACKAGE.MainActivity")
    )
    val toggle = find("Touch counting") ?: error("No touch counting toggle")
    if (!toggle.isChecked) toggle.click()
    device.waitForIdle()
}

/** Taps the counter [times] times, a little under the 150 ms repeat guard apart. */
internal fun MacrobenchmarkScope.tapCounter(times: Int) {
    val pad = device.wait(Until.findObject(By.descStartsWith("Count ")), TIMEOUT_MS) ?: error("No counter")
    val center = pad.visibleCenter
    repeat(times) {
        device.click(center.x, center.y)
        Thread.sleep(160)
    }
}

internal fun MacrobenchmarkScope.stopSession() {
    find("Stop")?.click()
    device.waitForIdle()
}

/** Home → each screen and back; History is flung through. */
internal fun MacrobenchmarkScope.visitScreens() {
    for (screen in listOf("History", "Goals", "Settings")) {
        find(screen)?.click() ?: continue
        device.waitForIdle()
        if (screen != "Goals") {
            device.findObject(By.scrollable(true))?.let { list ->
                list.setGestureMargin(device.displayWidth / 5)
                repeat(2) { list.fling(Direction.DOWN) }
                list.fling(Direction.UP)
            }
        }
        device.pressBack()
        device.waitForIdle()
    }
}

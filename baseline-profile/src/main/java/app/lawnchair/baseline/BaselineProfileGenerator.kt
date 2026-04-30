package app.lawnchair.baseline

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
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
    @RequiresApi(Build.VERSION_CODES.P)
    val rule = BaselineProfileRule()

    @Test
    @RequiresApi(Build.VERSION_CODES.P)
    fun generate() {
        rule.collect(Constants.PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()

            // Profile the settings entry point — PreferenceActivity + Preferences()
            // composable, NavHost setup, PreferencesDashboard.
            openSettings()

            // Profile PreferenceScaffold factory cold path:
            // AppBarLayout, CollapsingToolbarLayout, MaterialToolbar,
            // StretchNestedScrollView, ContextThemeWrapper, LayoutInflater.inflate.
            // Also profiles the Compose runtime first-composition path.
            navigateToScreen("General")
            device.pressBack()

            // Profile a second heavy screen to warm remaining Compose paths
            // that may have been skipped during General's composition.
            navigateToScreen("Dock")
            device.pressBack()

            // Profile About — has the most items in EagerLazyListScope.
            navigateToScreen("About")
            device.pressBack()

            device.pressBack()
        }
    }

    private fun MacrobenchmarkScope.openSettings() {
        // Long-press on empty space on the home screen to open the popup.
        device.click(device.displayWidth / 2, device.displayHeight * 2 / 3)
        device.waitForIdle()

        // Popup order: Wallpapers, Widgets, Apps list, Home settings (last).
        // Wait for the popup to fully appear before tapping.
        val homeSettings = device.wait(
            Until.findObject(By.text("Home settings")),
            2_000,
        )
        homeSettings?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.navigateToScreen(label: String) {
        val item = device.wait(
            Until.findObject(By.textContains(label)),
            2_000,
        ) ?: return
        item.click()
        // Wait for the CollapsingToolbarLayout title to appear — this confirms
        // PreferenceScaffold factory has run and the screen is fully drawn.
        device.wait(Until.findObject(By.textContains(label)), 3_000)
        // Scroll down to profile item rendering below the fold.
        device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.5f)
        device.waitForIdle()
    }
}

package app.lawnchair.baseline

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
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
    fun generateLauncherAndSettingsDashboard() {
        rule.collect(
            packageName = Constants.PACKAGE_NAME,
            maxIterations = 6,
            stableIterations = 2,
        ) {
            // Profile LawnchairLauncher cold start, home screen, and app drawer.
            pressHome()
            startActivityAndWait()
            swipeUpAppDrawer()

            // Launch PreferenceActivity directly so settings profiling does not depend on
            // launcher default-home state or home-screen popup coordinates.
            openSettingsDashboard()

            // Profile Settings search overlay, filtering, and LazyColumn segmented items.
            exerciseSettingsSearch()

            // Profile every top-level Compose settings screen in dashboard order.
            openDashboardCategory("General") {
                openSubScreen("Icon shape")
                openSubScreen("Accent color")
                openSubScreen("Color style")
                scrollCurrentScreen()
            }
            openDashboardCategory("Home screen")
            openDashboardCategory("At a Glance")
            openDashboardCategory("Dock")
            openDashboardCategory("App drawer")
            openDashboardCategory("Search bar") {
                scrollCurrentScreen()
                // Profile the second tab ("App drawer") in TwoTabPreferenceLayout.
                clickTextIfPresent("App drawer", timeoutMs = 1_500)
                settleTransition()
                scrollCurrentScreen()
            }
            openDashboardCategory("Folders")
            openDashboardCategory("Gestures")
            openDashboardCategory("Backup and restore")
            openDashboardCategory("Extras")
            openDashboardCategory("About")
        }
    }

    @Test
    @RequiresApi(Build.VERSION_CODES.P)
    fun generateSettingsSubpages() {
        rule.collect(
            packageName = Constants.PACKAGE_NAME,
            maxIterations = 6,
            stableIterations = 2,
        ) {
            // Profile all deep-linked settings sub-pages and conditional screens
            // (e.g., Recents screen / Quickstep which is hidden on the dashboard on emulators).
            SETTINGS_SUBPAGE_DEEP_LINKS.forEach { path ->
                openSettingsDeepLink(path)
                scrollCurrentScreen()
            }
        }
    }

    private fun MacrobenchmarkScope.swipeUpAppDrawer() {
        swipeDownContent()
        scrollCurrentScreen()
        device.pressBack()
        settleTransition()
    }

    private fun MacrobenchmarkScope.openSettingsDashboard() {
        val intent = Intent("android.intent.action.APPLICATION_PREFERENCES")
            .setPackage(packageName)
        startActivityAndWait(intent)
        device.wait(Until.hasObject(By.text("General")), 5_000)
    }

    private fun MacrobenchmarkScope.openSettingsDeepLink(path: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lawnchair://settings/$path"))
            .setPackage(packageName)
        startActivityAndWait(intent)
        settleTransition()
    }

    private fun MacrobenchmarkScope.exerciseSettingsSearch() {
        if (!clickTextIfPresent("Search settings", timeoutMs = 2_000)) return
        settleTransition()

        try {
            val input = device.wait(Until.findObject(By.focused(true)), 1_500)
                ?: device.findObject(By.clazz("android.widget.EditText"))
            if (input != null) {
                input.text = "icon"
                settleTransition()
                scrollCurrentScreen()
            }
        } catch (_: StaleObjectException) {
            // Compose may recompose the search field during focus transition.
        }

        // Dismiss IME and SearchOverlay until PreferencesDashboard is visible again.
        repeat(3) {
            if (device.hasObject(By.text("General"))) return
            device.pressBack()
            settleTransition()
        }
    }

    private fun MacrobenchmarkScope.openDashboardCategory(
        label: String,
        onScreen: MacrobenchmarkScope.() -> Unit = { scrollCurrentScreen() },
    ) {
        if (!clickTextScrollingDown(label)) return
        settleTransition()
        onScreen()
        device.pressBack()
        settleTransition()
    }

    private fun MacrobenchmarkScope.openSubScreen(label: String) {
        if (!clickTextScrollingDown(label)) return
        settleTransition()
        scrollCurrentScreen()
        device.pressBack()
        settleTransition()
    }

    private fun MacrobenchmarkScope.clickTextScrollingDown(label: String): Boolean {
        if (clickTextIfPresent(label, timeoutMs = 1_500)) return true
        repeat(4) {
            swipeDownContent()
            if (clickTextIfPresent(label, timeoutMs = 500)) return true
        }
        return false
    }

    private fun MacrobenchmarkScope.clickTextIfPresent(label: String, timeoutMs: Long): Boolean {
        repeat(2) {
            val target = device.wait(Until.findObject(By.text(label)), timeoutMs) ?: return false
            try {
                target.click()
                return true
            } catch (_: StaleObjectException) {
                settleTransition()
            }
        }
        return false
    }

    private fun MacrobenchmarkScope.scrollCurrentScreen() {
        swipeDownContent()
    }

    private fun MacrobenchmarkScope.swipeDownContent() {
        val centerX = device.displayWidth / 2
        device.swipe(
            centerX,
            device.displayHeight * 3 / 4,
            centerX,
            device.displayHeight / 4,
            15,
        )
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.settleTransition() {
        device.waitForIdle()
        SystemClock.sleep(300)
    }

    private companion object {
        val SETTINGS_SUBPAGE_DEEP_LINKS = listOf(
            "quickstep",
            "general-iconpack",
            "general-themedicons",
            "general-advanced-icons",
            "home-screen-grid",
            "home-screen-popup-editor",
            "dock-search-provider",
            "app-drawer-hidden-apps",
            "app-drawer-folder",
            "predictions",
            "experimental-features",
            "about-licenses",
            "create-backup",
        )
    }
}

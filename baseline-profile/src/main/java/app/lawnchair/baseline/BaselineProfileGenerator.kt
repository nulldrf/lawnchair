package app.lawnchair.baseline

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
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
                scrollCurrentScreen()
                openSubScreen("Icon shape")
                openSubScreen("Accent color")
                openSubScreen("Color style")
            }
            openDashboardCategory("Home screen")
            openDashboardCategory("At a Glance")
            openDashboardCategory("Dock")
            openDashboardCategory("App drawer")
            openDashboardCategory("Search bar") {
                scrollCurrentScreen()
                // Profile the second tab ("App drawer") in TwoTabPreferenceLayout.
                device.wait(Until.findObject(By.text("App drawer")), 1_500)?.click()
                device.waitForIdle()
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
        val centerX = device.displayWidth / 2
        device.swipe(
            centerX,
            device.displayHeight * 3 / 4,
            centerX,
            device.displayHeight / 4,
            20,
        )
        device.waitForIdle()
        scrollCurrentScreen()
        device.pressBack()
        device.waitForIdle()
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
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.exerciseSettingsSearch() {
        val searchBar = device.wait(Until.findObject(By.text("Search settings")), 2_000) ?: return
        searchBar.click()
        device.waitForIdle()

        val input = device.wait(Until.findObject(By.focused(true)), 1_500)
            ?: device.findObject(By.clazz("android.widget.EditText"))
        if (input != null) {
            input.text = "icon"
            device.waitForIdle()
            scrollCurrentScreen()
        }

        // Dismiss IME and SearchOverlay until PreferencesDashboard is visible again.
        repeat(3) {
            if (device.hasObject(By.text("General"))) return
            device.pressBack()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.openDashboardCategory(
        label: String,
        onScreen: MacrobenchmarkScope.() -> Unit = { scrollCurrentScreen() },
    ) {
        val item = findObjectScrollingDown(label) ?: return
        item.click()
        device.waitForIdle()
        onScreen()
        device.pressBack()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openSubScreen(label: String) {
        val item = findObjectScrollingDown(label) ?: return
        item.click()
        device.waitForIdle()
        scrollCurrentScreen()
        device.pressBack()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.findObjectScrollingDown(label: String): UiObject2? {
        device.wait(Until.findObject(By.text(label)), 1_500)?.let { return it }
        repeat(4) {
            val scrollable = device.findObject(By.scrollable(true)) ?: return null
            scrollable.setGestureMargin(device.displayWidth / 5)
            val scrolled = scrollable.scroll(Direction.DOWN, 0.7f)
            device.waitForIdle()
            device.findObject(By.text(label))?.let { return it }
            if (!scrolled) return null
        }
        return null
    }

    private fun MacrobenchmarkScope.scrollCurrentScreen() {
        val scrollable = device.findObject(By.scrollable(true)) ?: return
        scrollable.setGestureMargin(device.displayWidth / 5)
        scrollable.scroll(Direction.DOWN, 0.6f)
        device.waitForIdle()
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

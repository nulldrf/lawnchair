package app.lawnchair

import android.content.Context
import androidx.compose.runtime.RememberObserver
import app.lawnchair.views.LauncherPreviewView
import com.android.launcher3.InvariantDeviceProfile

class LauncherPreviewManager(private val context: Context) : RememberObserver {

    private var activePreview: LauncherPreviewView? = null
    private var lastIdp: InvariantDeviceProfile? = null
    private var lastForcePortrait: Boolean? = null
    private var lastOrientation: Int? = null
    private var lastReloadKey: Any? = null
    private var lastHotseatBackgroundKey: Any? = null

    fun createPreviewView(
        idp: InvariantDeviceProfile,
        forcePortrait: Boolean = false,
    ): LauncherPreviewView {
        destroyActivePreview()
        activePreview = LauncherPreviewView(context, idp, forcePortrait = forcePortrait)
        lastIdp = idp
        lastForcePortrait = forcePortrait
        return activePreview!!
    }

    fun acquirePreviewView(
        idp: InvariantDeviceProfile,
        forcePortrait: Boolean = false,
        orientation: Int = context.resources.configuration.orientation,
        reloadKey: Any? = null,
        hotseatBackgroundKey: Any? = null,
    ): LauncherPreviewView {
        destroyActivePreview()
        val created = LauncherPreviewView(context, idp, forcePortrait = forcePortrait)
        activePreview = created
        lastIdp = idp
        lastForcePortrait = forcePortrait
        lastOrientation = orientation
        lastReloadKey = reloadKey
        lastHotseatBackgroundKey = hotseatBackgroundKey
        return created
    }

    fun syncPreviewView(
        previewView: LauncherPreviewView,
        idp: InvariantDeviceProfile,
        forcePortrait: Boolean = false,
        orientation: Int = context.resources.configuration.orientation,
        reloadKey: Any? = null,
        hotseatBackgroundKey: Any? = null,
    ) {
        if (activePreview !== previewView) return

        val needsReload = lastIdp != idp ||
            lastForcePortrait != forcePortrait ||
            lastOrientation != orientation ||
            lastReloadKey != reloadKey

        if (needsReload) {
            lastIdp = idp
            lastForcePortrait = forcePortrait
            lastOrientation = orientation
            lastReloadKey = reloadKey
            lastHotseatBackgroundKey = hotseatBackgroundKey
            previewView.update(idp, forcePortrait)
        } else if (lastHotseatBackgroundKey != hotseatBackgroundKey) {
            lastHotseatBackgroundKey = hotseatBackgroundKey
            previewView.refreshHotseatBackground()
        }
    }

    fun releasePreviewView(previewView: LauncherPreviewView) {
        if (activePreview === previewView) {
            destroyActivePreview()
        } else {
            previewView.destroy()
        }
    }

    fun destroyActivePreview() {
        activePreview?.destroy()
        activePreview = null
        lastIdp = null
        lastForcePortrait = null
        lastOrientation = null
        lastReloadKey = null
        lastHotseatBackgroundKey = null
    }

    override fun onRemembered() {
    }

    override fun onForgotten() {
        destroyActivePreview()
    }

    override fun onAbandoned() {
        destroyActivePreview()
    }
}

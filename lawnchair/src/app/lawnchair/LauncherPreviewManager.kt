package app.lawnchair

import android.content.Context
import androidx.compose.runtime.RememberObserver
import app.lawnchair.views.LauncherPreviewView
import com.android.launcher3.InvariantDeviceProfile

class LauncherPreviewManager(private val context: Context) : RememberObserver {

    private var activePreview: LauncherPreviewView? = null

    fun createPreviewView(
        idp: InvariantDeviceProfile,
        forcePortrait: Boolean = false,
    ): LauncherPreviewView {
        destroyActivePreview()
        activePreview = LauncherPreviewView(context, idp, forcePortrait = forcePortrait)
        return activePreview!!
    }

    private fun destroyActivePreview() {
        activePreview?.destroy()
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

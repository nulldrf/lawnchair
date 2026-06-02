package app.lawnchair.allapps.views

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import app.lawnchair.font.FontManager
import app.lawnchair.launcher
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.util.AppInfo
import app.lawnchair.util.AppInfoHelper
import app.lawnchair.util.ImageViewWrapper
import com.android.app.search.LayoutType
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R

class SearchResultRightLeftIcon(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs),
    SearchResultView {

    private val launcher = context.launcher
    private var grid: DeviceProfile = launcher.deviceProfile
    private lateinit var title: TextView
    private lateinit var avatar: SearchResultIcon
    private lateinit var call: ImageView
    private lateinit var message: ImageView
    private lateinit var preview: ImageViewWrapper
    private lateinit var textRows: LinearLayout
    private val appInfoHelper = AppInfoHelper(context)
    private var defPhoneAppInfo: AppInfo? = null
    private var defSmsAppInfo: AppInfo? = null
    private var isSmall = false

    private var flags = 0

    override fun onFinishInflate() {
        super.onFinishInflate()
        isSmall = id == R.id.search_result_small_icon_row_left_right
        defPhoneAppInfo = appInfoHelper.getDefaultPhoneAppInfo()
        defSmsAppInfo = appInfoHelper.getDefaultMessageAppInfo()
        onFocusChangeListener = launcher.focusHandler
        title = ViewCompat.requireViewById(this, R.id.title)
        textRows = ViewCompat.requireViewById(this, R.id.text_rows)
        avatar = ViewCompat.requireViewById(this, R.id.avatar)
        call = ViewCompat.requireViewById(this, R.id.icon2)
        message = ViewCompat.requireViewById(this, R.id.icon1)
        preview = ViewCompat.requireViewById(this, R.id.files_preview)
        FontManager.INSTANCE.get(context).setCustomFont(title, R.id.font_body)

        // Mirror avatar's exact size onto files_preview and apply CENTER_VERTICAL
        // gravity so photo thumbnails align with the Google G icon and all other
        // icons in the list. Must use LinearLayout.LayoutParams — assigning a bare
        // ViewGroup.MarginLayoutParams silently drops the gravity set in XML.
        val iconSizeWithPadding = avatar.iconSize + avatar.compoundDrawablePadding
        val iconMargin = resources.getDimensionPixelSize(R.dimen.search_result_margin)
        preview.layoutParams = LinearLayout.LayoutParams(iconSizeWithPadding, iconSizeWithPadding).apply {
            gravity = Gravity.CENTER_VERTICAL
            marginStart = iconMargin
        }

        // Set gravity once here — it survives orientation changes and view recycling.
        // The actual height is set to a concrete pixel value in bind() after setUpdateResources()
        // has determined the card height, so MATCH_PARENT is never needed.
        textRows.gravity = Gravity.CENTER_VERTICAL

        setUpdateResources()
    }

    private fun setUpdateResources() {
        if (isSmall) {
            message.setImageDrawable(defSmsAppInfo?.appIcon)
            call.setImageDrawable(defPhoneAppInfo?.appIcon)
            call.visibility = VISIBLE
            message.visibility = VISIBLE
            avatar.visibility = VISIBLE
            preview.visibility = GONE
        } else {
            call.visibility = GONE
            message.visibility = GONE
            avatar.visibility = GONE
            preview.visibility = VISIBLE
        }
        // Both file and contact rows use fixed heights.
        // File rows match search_result_row_height — the same height as the
        // Google G card — so all cards in the list have a consistent size.
        val heightRes = if (isSmall) {
            resources.getDimensionPixelSize(R.dimen.search_result_small_row_height)
        } else {
            resources.getDimensionPixelSize(R.dimen.search_result_row_height)
        }
        val layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            heightRes,
        )
        layoutParams.leftMargin = 0
        layoutParams.rightMargin = 0
        this.layoutParams = layoutParams

        // Give textRows the exact same pixel height as the card so that
        // gravity="center_vertical" has real space to work with. MATCH_PARENT
        // is unreliable here because it resolves against the root view's measured
        // height, which may be 0 during onFinishInflate and early layout passes.
        (textRows.layoutParams as LinearLayout.LayoutParams).height = heightRes
        textRows.requestLayout()
    }

    override val isQuickLaunch: Boolean
        get() = hasFlag(flags, SearchResultView.FLAG_QUICK_LAUNCH)

    override val titleText: CharSequence? get() = title.text

    override fun launch(): Boolean {
        val logTag = "Contact or files"
        Log.d(logTag, "in launch")
        Log.d(logTag, performClick().toString())
        return true
    }

    override fun bind(
        target: SearchTargetCompat,
        shortcuts: List<SearchTargetCompat>,
    ) {
        flags = getFlags(target.extras)
        title.text = target.searchAction?.title
        val isNewFile = target.resultType == SearchTargetCompat.RESULT_TYPE_FILE_TILE &&
            target.layoutType == LayoutType.THUMBNAIL
        val isFile = !isSmall && isNewFile

        if (!isFile) {
            avatar.bind(target) {
                title.text = it.title
                tag = it
            }
            val number = target.searchAction?.subtitle.toString()
            message.setOnClickListener {
                defSmsAppInfo?.let { appInfo ->
                    launchApp(appInfo.packageName, number)
                }
            }
            call.setOnClickListener {
                defPhoneAppInfo?.let { appInfo ->
                    launchApp(appInfo.packageName, number)
                }
            }
        }

        if (!isFile) {
            // Contact row: compact single-line horizontal layout.
            isSmall = true
            textRows.orientation = HORIZONTAL
            title.maxLines = 1
            title.isSingleLine = true
            setUpdateResources()
        }

        if (isFile) {
            val icon = target.searchAction?.icon
            val isPhotoBitmap = icon != null &&
                (icon.type == Icon.TYPE_BITMAP || icon.type == Icon.TYPE_ADAPTIVE_BITMAP)

            if (isPhotoBitmap) {
                // Actual photo/image thumbnail — ImageViewWrapper's CENTER_CROP +
                // rounded-rect clip looks great here, keep it.
                preview.visibility = VISIBLE
                avatar.visibility = GONE
                preview.setImageIcon(icon)
            } else {
                // Generic vector file icon (torrent, zip, folder, unknown…).
                // Route through SearchResultIcon so it gets the same circle
                // treatment as web suggestion and settings icons.
                preview.visibility = GONE
                avatar.visibility = VISIBLE
                avatar.forceCircleIcon = true
                avatar.bind(target) { title.text = it.title }
            }

            textRows.orientation = VERTICAL
            title.isSingleLine = false
            title.maxLines = 3
        }

        if (shouldHandleClick(target)) {
            setOnClickListener {
                target.searchAction?.intent?.let { intent -> handleSearchTargetClick(context, intent) }
            }
        }
    }

    private fun launchApp(packageName: String, phoneNumber: String? = null) {
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        launchIntent ?: return
        if (packageName == defSmsAppInfo?.packageName) {
            val smsIntent = Intent(Intent.ACTION_VIEW)
            smsIntent.data = Uri.parse("smsto:$phoneNumber")
            smsIntent.putExtra("address", phoneNumber)
            handleSearchTargetClick(context, smsIntent)
        } else if (packageName == defPhoneAppInfo?.packageName && phoneNumber != null) {
            val phoneIntent = Intent(Intent.ACTION_DIAL)
            phoneIntent.data = Uri.parse("tel:$phoneNumber")
            handleSearchTargetClick(context, phoneIntent)
        }
    }
}
